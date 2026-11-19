package com.ankiduel.server.auth

import com.ankiduel.server.db.UsersTable
import com.ankiduel.shared.protocol.AuthResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AuthService(
    private val db: Database,
    private val jwtSecret: String,
    private val issuer: String = "ankiduel",
    private val audience: String = "ankiduel-clients"
) {
    private val algorithm = Algorithm.HMAC256(jwtSecret)
    private val secureRandom = SecureRandom()

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun register(rawUsername: String, password: String): AuthResponse {
        val username = rawUsername.trim()
        require(username.length in 3..24) { "Le pseudo doit contenir entre 3 et 24 caractères." }
        require(password.length >= 4) { "Le mot de passe doit contenir au moins 4 caractères." }

        val userId = UUID.randomUUID().toString()
        val hash = hashPassword(password)
        val now = System.currentTimeMillis()

        transaction(db) {
            val exists = UsersTable.selectAll()
                .where { UsersTable.username eq username }
                .any()
            if (exists) {
                throw IllegalArgumentException("Ce pseudo est déjà utilisé.")
            }
            UsersTable.insert {
                it[id] = userId
                it[UsersTable.username] = username
                it[passwordHash] = hash
                it[createdAt] = now
            }
        }

        return AuthResponse(
            token = createToken(userId, username),
            userId = userId,
            username = username
        )
    }

    fun login(rawUsername: String, password: String): AuthResponse {
        val username = rawUsername.trim()
        val row = transaction(db) {
            UsersTable.selectAll()
                .where { UsersTable.username eq username }
                .firstOrNull()
        } ?: throw SecurityException("Identifiants invalides.")

        val storedHash = row[UsersTable.passwordHash]
        if (!verifyPassword(password, storedHash)) {
            throw SecurityException("Identifiants invalides.")
        }

        val userId = row[UsersTable.id]
        return AuthResponse(
            token = createToken(userId, username),
            userId = userId,
            username = username
        )
    }

    fun createToken(userId: String, username: String): String {
        val expiresAt = Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    private fun hashPassword(password: String): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val derived = pbkdf2(password, salt)
        val encoder = Base64.getEncoder()
        return "${encoder.encodeToString(salt)}:${encoder.encodeToString(derived)}"
    }

    private fun verifyPassword(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[0])
        val expected = decoder.decode(parts[1])
        val actual = pbkdf2(password, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 65_536, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

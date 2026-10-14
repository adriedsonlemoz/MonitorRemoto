package br.dev.monitorremoto.adb

import android.content.Context
import br.dev.monitorremoto.security.SecretVault
import java.security.PrivateKey
import java.security.cert.Certificate
import java.io.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Exportable RSA for Conscrypt; persisted only inside the existing Keystore AES-GCM vault. */
object AdbIdentity {
    class Identity(val privateKey: PrivateKey, val certificate: Certificate)
    private val slot = SecretVault.profileKeyName("monitor-remoto-adb-identity-v1")
    @Synchronized fun load(context: Context): Identity {
        SecretVault.load(context, slot)?.let { blob ->
            try {
                DataInputStream(ByteArrayInputStream(blob)).use { input ->
                    val n = input.readInt(); require(n in 512..8192 && n < blob.size)
                    val privateBytes = ByteArray(n).also(input::readFully)
                    val key = try { KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateBytes)) } finally { privateBytes.fill(0) }
                    val cert = CertificateFactory.getInstance("X.509").generateCertificate(input)
                    return Identity(key, cert)
                }
            } finally { blob.fill(0) }
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val provider = BouncyCastleProvider()
        val name = X500Name("CN=Monitor Remoto ADB")
        val cert = JcaX509CertificateConverter().setProvider(provider).getCertificate(
            JcaX509v3CertificateBuilder(name, BigInteger(128, SecureRandom()), Date(System.currentTimeMillis()-86400000),
                Date(System.currentTimeMillis()+10L*365*86400000), name, pair.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(pair.private)))
        val output = ByteArrayOutputStream()
        val keyBytes = pair.private.encoded
        DataOutputStream(output).use { it.writeInt(keyBytes.size); it.write(keyBytes); it.write(cert.encoded) }
        keyBytes.fill(0)
        val blob = output.toByteArray()
        try { SecretVault.save(context, slot, blob) } finally { blob.fill(0) }
        return Identity(pair.private, cert)
    }
    @Synchronized fun forget(context: Context) = SecretVault.delete(context, slot)
}

package net.raccoon.sourcecrypt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

open class CryptPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("crypt", CryptExtension::class.java, project)

        project.tasks.register("decryptResources", DecryptTask::class.java) {
            it.getInclude().set(extension.getResources().include)
            it.getPassword().set(extension.password)
            it.getSalt().set(extension.salt)
        }
        project.tasks.register("encryptResources", EncryptTask::class.java) {
            it.getInclude().set(extension.getResources().include)
            it.getPassword().set(extension.password)
            it.getSalt().set(extension.salt)
        }

        project.tasks.getByName("processResources").dependsOn(project.tasks.withType(EncryptTask::class.java))

        project.afterEvaluate { project ->
            project.extensions.getByType(SourceSetContainer::class.java).getByName("main") {
                it.java {
                    it.srcDir(File("${project.buildDir}/generated/kotlin"))
                }
            }
        }

        val generateCryptorTask = project.tasks.create("generateCryptor") {
            it.group = "crypt"
            it.doFirst {
                val directory = it.project.file("${project.buildDir}/generated/kotlin/net/raccoon/crypt")
                directory.mkdirs()
                it.project.file("${directory.absolutePath}/Cryptor.java").apply { createNewFile() }
                    .writeText(
                        """
                        package net.raccoon.crypt;
                        
                        import javax.crypto.Cipher;
                        import javax.crypto.SecretKeyFactory;
                        import javax.crypto.spec.IvParameterSpec;
                        import javax.crypto.spec.PBEKeySpec;
                        import javax.crypto.spec.SecretKeySpec;
                        import java.nio.charset.StandardCharsets;
                        import java.util.Base64;
                        
                        public class Cryptor {
                            private static final Base64.Decoder BASE_64_DECODER = Base64.getDecoder();
                            private static final Base64.Encoder BASE_64_ENCODER = Base64.getEncoder();
                        
                            public static byte[] decrypt(byte[] encrypted, char[] password, byte[] salt)
                                    throws java.security.GeneralSecurityException {
                        
                                final Cipher cipher = buildCipher(password, salt, Cipher.DECRYPT_MODE);
                                return cipher.doFinal(BASE_64_DECODER.decode(encrypted));
                            }
                        
                            public static String decryptToString(String encrypted, char[] password, byte[] salt)
                                    throws java.security.GeneralSecurityException {
                                return new String(decrypt(encrypted.getBytes(StandardCharsets.UTF_8), password, salt), StandardCharsets.UTF_8);
                            }
                        
                            public static byte[] encrypt(byte[] raw, char[] password, byte[] salt)
                                    throws java.security.GeneralSecurityException {
                        
                                final Cipher cipher = buildCipher(password, salt, Cipher.ENCRYPT_MODE);
                                return BASE_64_ENCODER.encode(cipher.doFinal(raw));
                            }
                        
                            public static String encryptToString(String raw, char[] password, byte[] salt)
                                    throws java.security.GeneralSecurityException {
                                return new String(encrypt(raw.getBytes(StandardCharsets.UTF_8), password, salt), StandardCharsets.UTF_8);
                            }
                        
                            public static Cipher buildCipher(char[] password, byte[] salt, int cipherOpMode) throws java.security.GeneralSecurityException {
                                byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
                                final IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
                                final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                                final PBEKeySpec keySpec = new PBEKeySpec(password, salt, 65536, 256);
                                final SecretKeySpec secretKey = new SecretKeySpec(keyFactory.generateSecret(keySpec).getEncoded(), "AES");
                        
                                final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                                cipher.init(cipherOpMode, secretKey, ivParameterSpec);
                                return cipher;
                            }
                        }
                        """.trimIndent()
                    )
            }
        }

        project.tasks.findByName("compileKotlin")?.dependsOn(generateCryptorTask)
        project.tasks.findByName("compileJava")?.dependsOn(generateCryptorTask)
    }

}

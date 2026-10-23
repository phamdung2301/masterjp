package com.masterjp.config;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Bai hoc xuong mau: MongoDB driver LANG LE dung database "test" khi URI
 * khong chua ten DB — khong bao loi, khong canh bao; app chay binh thuong
 * nhung doc/ghi sai database.
 *
 * Config nay ghi de MongoDatabaseFactory cua Boot de ep ten database EXPLICIT
 * theo thu tu uu tien: DB trong URI > MONGODB_DATABASE > fail-fast.
 * KHONG BAO GIO roi ve "test" mot cach im lang.
 */
@Configuration
@EnableTransactionManagement // Phase D — F-044: @Transactional cho fulfill (multi-document)
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:}")
    private String uri;

    @Value("${spring.data.mongodb.database:}")
    private String configuredDatabase;

    /**
     * Tao MongoClient EXPLICIT tu URI — khong phu thuoc vao auto-config cua
     * Boot (trai nghiem Render 2026-10-15: bean MongoClient tu auto-config
     * khong ton tai trong context dan toi UnsatisfiedDependencyException
     * khi khoi dong). Dinh nghia o day thi Boot tu back-off nho
     * @ConditionalOnMissingBean.
     */
    @Bean
    public MongoClient mongoClient() {
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException(
                    "MONGODB_URI chua duoc cau hinh. Tu choi khoi dong.");
        }
        return MongoClients.create(uri);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        String database = resolveDatabaseName();
        return new SimpleMongoClientDatabaseFactory(mongoClient, database);
    }

    /**
     * Phase D — F-044: transaction manager cho @Transactional trong
     * PaymentFulfillmentService.fulfill (multi-document: invoice + sepay_transactions
     * + users + audit_logs). Yeu cau MongoDB replica set (Atlas dat) moi chay duoc.
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    private String resolveDatabaseName() {
        // 1. Database ghi trong path cua URI (vd. mongodb+srv://.../masterjp?...)
        if (uri != null && !uri.isBlank()) {
            try {
                String fromUri = new ConnectionString(uri).getDatabase();
                if (fromUri != null && !fromUri.isBlank()) {
                    return fromUri;
                }
            } catch (IllegalArgumentException ignored) {
                // URI khong parse duoc -> de Boot bao loi o tang tao client
            }
        }
        // 2. Bien cau hinh explicit: spring.data.mongodb.database (ENV MONGODB_DATABASE)
        if (configuredDatabase != null && !configuredDatabase.isBlank()) {
            return configuredDatabase;
        }
        // 3. TU CHOI khoi dong thay vi lang le dung "test"
        throw new IllegalStateException(
                "Ten MongoDB database chua duoc cau hinh (URI khong chua ten DB va thieu MONGODB_DATABASE). "
                        + "Tu choi khoi dong de tranh ghi nham vao database 'test'.");
    }
}

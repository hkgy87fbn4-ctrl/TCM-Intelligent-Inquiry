package com.tcm.inquiry.infrastructure.vectorstore;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

/**
 * 生产 / 本地开发（非 test、ci）：向量持久化在 Redis Stack（RediSearch 向量索引）。
 * <p>
 * 显式声明 {@link RedisVectorStore.MetadataField}，以便 RAG 删除与按 kb_id / 文献库 id 过滤时，
 * RediSearch 能正确解析 metadata（否则过滤条件可能不生效）。
 * </p>
 */
@Configuration
@Profile("!test & !ci")
public class RedisStackVectorStoreConfig {

    /**
     * 与 spring.data.redis.* 对齐的 Jedis 连接池；destroyMethod 在应用关闭时释放 TCP。
     */
    @Bean(destroyMethod = "close")
    public JedisPooled jedisPooled(RedisProperties redisProperties) {
        String host = redisProperties.getHost() != null ? redisProperties.getHost() : "localhost";
        int port = redisProperties.getPort();
        int timeout = redisProperties.getTimeout() != null ? (int) redisProperties.getTimeout().toMillis() : 2000;
        HostAndPort hp = new HostAndPort(host, port);

        String user = redisProperties.getUsername();
        String pass = redisProperties.getPassword();

        if (StringUtils.hasText(pass)) {
            DefaultJedisClientConfig.Builder cfg =
                    DefaultJedisClientConfig.builder()
                            .socketTimeoutMillis(timeout)
                            .connectionTimeoutMillis(timeout)
                            .password(pass);
            if (StringUtils.hasText(user)) {
                cfg.user(user);
            }
            // Jedis 5：构造参数顺序为 HostAndPort，再 JedisClientConfig
            return new JedisPooled(hp, cfg.build());
        }

        DefaultJedisClientConfig clientConfig =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build();
        return new JedisPooled(hp, clientConfig);
    }

    @Bean
    public VectorStore vectorStore(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.redis.index-name:tcm-spring-ai-index}") String indexName,
            @Value("${spring.ai.vectorstore.redis.prefix:tcm:emb:}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema) {
        // 与知识库 / 文献入库时写入的 metadata 键一致，类型选用 TAG/TEXT 以支持等值过滤表达式
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(initializeSchema)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("kb_id"),
                        RedisVectorStore.MetadataField.tag("file_id"),
                        RedisVectorStore.MetadataField.text("source"),
                        RedisVectorStore.MetadataField.tag("lit_collection_id"))
                .build();
    }
}

package com.monotron.support.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed configuration for the whole application.
 */
@ConfigurationProperties(prefix = "monotron")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Storage storage = new Storage();
    private final Tenancy tenancy = new Tenancy();
    private final Ai ai = new Ai();
    private final Billing billing = new Billing();
    private final Google google = new Google();

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public Storage getStorage() {
        return storage;
    }

    public Tenancy getTenancy() {
        return tenancy;
    }

    public Ai getAi() {
        return ai;
    }

    public Billing getBilling() {
        return billing;
    }

    public Google getGoogle() {
        return google;
    }

    public static class Jwt {
        /** HMAC-SHA256 signing secret. Must be at least 32 bytes. */
        private String secret = "monotron-dev-secret-change-me-in-production-0123456789abcdef";
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(30);
        private String issuer = "monotron";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:3000");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Storage {
        /** Directory where uploaded PDFs are kept. */
        private String directory = "./data/uploads";
        private long maxUploadBytes = 25L * 1024 * 1024;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public long getMaxUploadBytes() {
            return maxUploadBytes;
        }

        public void setMaxUploadBytes(long maxUploadBytes) {
            this.maxUploadBytes = maxUploadBytes;
        }
    }

    public static class Tenancy {
        /**
         * Non-superuser role that tenant-bound transactions are downgraded to
         * with {@code SET LOCAL ROLE}, so that Row Level Security is enforced.
         * An empty value disables the downgrade.
         */
        private String role = "monotron_app";

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class Ai {
        /** {@code auto} uses the remote provider when an API key is configured, otherwise the local one. */
        private String provider = "auto";
        private final OpenAi openai = new OpenAi();
        private final Chunking chunking = new Chunking();
        private final Retrieval retrieval = new Retrieval();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public OpenAi getOpenai() {
            return openai;
        }

        public Chunking getChunking() {
            return chunking;
        }

        public Retrieval getRetrieval() {
            return retrieval;
        }

        public static class OpenAi {
            private String baseUrl = "https://api.openai.com/v1";
            private String apiKey = "";
            private String chatModel = "gpt-4o-mini";
            private String embeddingModel = "text-embedding-3-small";
            private Duration timeout = Duration.ofSeconds(60);

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getChatModel() {
                return chatModel;
            }

            public void setChatModel(String chatModel) {
                this.chatModel = chatModel;
            }

            public String getEmbeddingModel() {
                return embeddingModel;
            }

            public void setEmbeddingModel(String embeddingModel) {
                this.embeddingModel = embeddingModel;
            }

            public Duration getTimeout() {
                return timeout;
            }

            public void setTimeout(Duration timeout) {
                this.timeout = timeout;
            }
        }

        public static class Chunking {
            private int chunkSize = 1100;
            private int chunkOverlap = 200;

            public int getChunkSize() {
                return chunkSize;
            }

            public void setChunkSize(int chunkSize) {
                this.chunkSize = chunkSize;
            }

            public int getChunkOverlap() {
                return chunkOverlap;
            }

            public void setChunkOverlap(int chunkOverlap) {
                this.chunkOverlap = chunkOverlap;
            }
        }

        public static class Retrieval {
            private int topK = 6;
            private double minScore = 0.15;

            public int getTopK() {
                return topK;
            }

            public void setTopK(int topK) {
                this.topK = topK;
            }

            public double getMinScore() {
                return minScore;
            }

            public void setMinScore(double minScore) {
                this.minScore = minScore;
            }
        }
    }

    /**
     * Google Identity Services. The client id is public and is also sent to the
     * browser so the sign-in button can be rendered; the token signature is
     * verified on the server with the same id as the audience.
     */
    public static class Google {
        private String clientId = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank();
        }
    }

    /**
     * Razorpay subscription billing. Prices are stored in the smallest currency
     * unit (paise for INR) because that is what Razorpay's API expects.
     */
    public static class Billing {
        private boolean enforce = true;
        private String provider = "razorpay";
        private String keyId = "";
        private String keySecret = "";
        private String webhookSecret = "";
        private String apiBaseUrl = "https://api.razorpay.com/v1";
        private String currency = "INR";
        private int monthlyAmount = 39900;
        private int yearlyAmount = 399000;
        private int trialDays = 3;
        private int monthlyCycles = 100;
        private int yearlyCycles = 10;
        private Duration timeout = Duration.ofSeconds(30);

        public boolean isEnforce() {
            return enforce;
        }

        public void setEnforce(boolean enforce) {
            this.enforce = enforce;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKeySecret() {
            return keySecret;
        }

        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public int getMonthlyAmount() {
            return monthlyAmount;
        }

        public void setMonthlyAmount(int monthlyAmount) {
            this.monthlyAmount = monthlyAmount;
        }

        public int getYearlyAmount() {
            return yearlyAmount;
        }

        public void setYearlyAmount(int yearlyAmount) {
            this.yearlyAmount = yearlyAmount;
        }

        public int getTrialDays() {
            return trialDays;
        }

        public void setTrialDays(int trialDays) {
            this.trialDays = trialDays;
        }

        public int getMonthlyCycles() {
            return monthlyCycles;
        }

        public void setMonthlyCycles(int monthlyCycles) {
            this.monthlyCycles = monthlyCycles;
        }

        public int getYearlyCycles() {
            return yearlyCycles;
        }

        public void setYearlyCycles(int yearlyCycles) {
            this.yearlyCycles = yearlyCycles;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}

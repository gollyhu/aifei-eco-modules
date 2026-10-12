# aifei-cache

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/JDK-1.8+-green.svg)]()

## 📖 Introduction

**aifei-cache** is a caching plugin module based on the [Aifei](https://gitee.com/gollyhu) framework, providing a unified cache abstraction layer with support for multiple backend implementations. It decouples business code from specific cache implementations.

## ✨ Features

- **Unified API**: `ICache` interface defines atomic operations (`put` / `get` / `evict` / `clear`) shared across all three implementations
- **Multiple Backends**: Caffeine (high-performance local), LocalCache (ConcurrentHashMap local), Redisson (distributed Redis)
- **Cache-Aside Support**: `get(key, supplier)` provides cache penetration protection with automatic backfill
- **Batch Operations**: `putAll` / `getAll` / `evict(...)` support batch processing
- **Configuration-Driven**: Load cache instances from config via the Aifei Plugin mechanism — zero-code backend switching
- **Pluggable Management**: `CacheManager` singleton manages multiple cache instances with runtime register/remove support

## 📦 Dependencies

### Compile Dependencies

| Dependency | Version | Description |
|------------|---------|-------------|
| [aifei](https://gitee.com/gollyhu) | 1.0.1 | Aifei framework core |
| aifei-log | 1.0.1 | Aifei logging module |
| [Caffeine](https://github.com/ben-manes/caffeine) | 2.9.3 | High-performance local cache library |
| [Redisson](https://github.com/redisson/redisson) | 4.4.0 | Redis distributed cache client |
| [Jedis](https://github.com/redis/jedis) | 7.5.0 | Redis Java client |

### Test Dependencies

| Dependency | Version | Description |
|------------|---------|-------------|
| JUnit | 4.13.2 | Unit testing framework |
| Mockito | 4.11.0 | Mock framework |
| SLF4J Simple | 2.0.18 | Test logging implementation |

## 🏗️ Architecture

```
ICache (Interface)
├── CaffeineCache          # Caffeine-based, capacity limit + stats
├── LocalCache             # ConcurrentHashMap-based, lazy eviction + background cleanup
└── RedissonCache          # Redisson RMapCache-based, distributed per-key TTL
```

### Core Components

| Component | Responsibility |
|-----------|---------------|
| `ICache` | Core interface defining `put` / `get` / `evict` / `clear` atomic operations |
| `CacheProvider` | Factory interface providing `buildCache(name, type, prop, prefix)` |
| `CacheManager` | Singleton manager for registering, retrieving, and removing cache instances |
| `CachePlugin` | Aifei Plugin implementation, loads main and extension caches on startup |
| `CacheUtil` | Static utility class for convenient access to the default cache instance |

## 🚀 Quick Start

### 1. Add Maven Dependency

```xml
<dependency>
    <groupId>cn.hg.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure Caches

Add to your Aifei config file (e.g., `app-config-dev.txt`):

```properties
# ════════════ Main Cache (Required) ════════════
cache.type = caffeine
cache.maxSize = 10000
cache.ttl = 3600

# ════════════ Extension Caches (Optional) ════════════
cache.names = userCache,redisCache

cache.userCache.type = local
cache.userCache.ttl = 7200

cache.redisCache.type = redisson
cache.redisCache.address = 127.0.0.1:6379
cache.redisCache.ttl = 1800
```

### 3. Usage

```java
// Method 1: via CacheUtil static methods (operates on default cache)
CacheUtil.put("key", "value", 600);
String val = CacheUtil.get("key");

// Method 2: via CacheManager to specify cache instance
ICache userCache = CacheManager.getCache("userCache");
userCache.put("uid:1001", userInfo);

// Method 3: Cache-aside pattern with fallback (cache penetration protection)
User user = CacheUtil.get("user:1001",
    () -> db.findById(1001), 300);
```

## 📋 API Reference

### ICache Core Methods

| Method | Description |
|--------|-------------|
| `put(key, value)` | Store with default TTL |
| `put(key, value, ttlSeconds)` | Store with specified TTL (seconds) |
| `<T> T get(key)` | Retrieve cached value |
| `<T> T get(key, supplier)` | Get-or-compute with default TTL |
| `<T> T get(key, supplier, ttlSeconds)` | Get-or-compute with specified TTL |
| `evict(keys...)` | Remove one or more keys |
| `clear()` | Clear all entries in this cache instance |
| `putAll(map, ttlSeconds)` | Batch store |
| `getAll(keys)` | Batch retrieve |
| `<T> T getNativeCache()` | Get underlying native cache instance (escape hatch) |
| `setTtl(key, ttlSeconds)` | Set per-key TTL (optional implementation) |
| `getTtl(key)` | Get remaining per-key TTL (optional implementation) |
| `getDefaultTtl()` | Get default TTL |
| `setDefaultTtl(ttlSeconds)` | Set default TTL |

### Provider Configuration Comparison

| Provider | Config Key | Default | Description |
|----------|-----------|---------|-------------|
| **Caffeine** | `maxSize` | 10000 | Max cache entries |
| | `ttl` | 3600 | Default TTL (seconds) |
| **Local** | `ttl` | 3600 | Default TTL (seconds), 0 = never expire |
| **Redisson** | `address` | 127.0.0.1:6379 | Redis server address |
| | `password` | - | Redis password |
| | `database` | 0 | Database index |
| | `ttl` | 3600 | Default TTL (seconds) |
| | `redissonJson` | - | Custom Redisson JSON config |

> **Config prefix rules**:
> - Main cache: `cache.` (e.g., `cache.type`, `cache.ttl`)
> - Extension cache: `cache.<name>.` (e.g., `cache.userCache.type`)
> - Custom Provider: specify via `cache.<name>.provider = com.example.MyProvider`

## 🔧 Configuration Rules

| Rule | Description |
|------|-------------|
| Main cache required | `cache.type` must be configured; missing it throws `CacheException` on startup |
| Extension cache declaration | Declared via `cache.names` comma-separated, e.g., `cache.names = c1,c2` |
| Reserved name | `main` is a reserved name for the main cache; extension caches must not use it |
| Default Provider mapping | caffeine → `CaffeineCacheProvider`<br>local → `LocalCacheProvider`<br>redisson → `RedissonCacheProvider` |
| Missing type skip | Extension caches without `cache.<name>.type` are skipped with a warning |

## 🔧 Commands

```bash
mvn compile                               # Compile project
mvn test                                  # Run all tests
mvn test -Dtest=CaffeineCacheTest,...     # Run standalone tests (no framework env needed)
mvn test-compile                          # Compile test code only
mvn package                               # Package
```

## 🤝 Contribution

1. Fork the repository
2. Create a `Feat_xxx` branch
3. Commit your code
4. Create a Pull Request

## 📄 License

[Apache License 2.0](LICENSE)

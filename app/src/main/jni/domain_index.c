#include <jni.h>
#include <stdint.h>
#include <string.h>

/* Read-only, big-endian domain index produced atomically by MappedDomainIndex. */
static uint32_t read32(const uint8_t *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) |
           ((uint32_t)p[2] << 8) | (uint32_t)p[3];
}

static int maybe_present(const uint8_t *bloom, uint32_t bits,
                         const uint8_t *name, size_t length) {
    uint32_t h1 = UINT32_C(0x811c9dc5);
    uint32_t h2 = h1 ^ UINT32_C(0x9e3779b9);
    for (size_t i = 0; i < length; ++i) {
        h1 = (h1 ^ name[i]) * UINT32_C(0x01000193);
        h2 = (h2 ^ name[i]) * UINT32_C(0x01000193);
    }
    h2 |= 1;
    for (uint32_t i = 0; i < 7; ++i) {
        uint32_t bit = (h1 + i * h2) & (bits - 1);
        if (!(bloom[bit >> 3] & (1u << (bit & 7)))) return 0;
    }
    return 1;
}

static int lookup(const uint8_t *data, size_t length, uint32_t count,
                  uint32_t offsets_start, uint32_t records_start,
                  const uint8_t *name, size_t name_length) {
    uint32_t low = 0, high = count;
    while (low < high) {
        uint32_t middle = low + (high - low) / 2;
        uint32_t offset = read32(data + offsets_start + middle * 4u);
        if ((size_t)records_start + offset + 2u > length) return -1;
        const uint8_t *record = data + records_start + offset;
        uint32_t size = record[0];
        if ((size_t)records_start + offset + 2u + size > length) return -1;
        size_t common = name_length < size ? name_length : size;
        int order = memcmp(name, record + 2, common);
        if (!order) order = name_length < size ? -1 : name_length > size ? 1 : 0;
        if (order == 0) return record[1];
        if (order < 0) high = middle;
        else low = middle + 1;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_miowser_app_adblock_NativeDomainLookup_nativeMatch(
        JNIEnv *env, jobject receiver, jobject buffer, jstring hostname) {
    (void)receiver;
    if (!buffer || !hostname) return -1;
    const uint8_t *data = (*env)->GetDirectBufferAddress(env, buffer);
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    if (!data || capacity < 88 || capacity > INT32_MAX) return -1;
    size_t length = (size_t)capacity;
    if (memcmp(data, "MWDOM001", 8) || read32(data + 8) != 1) return -1;
    uint32_t bits = read32(data + 12);
    uint32_t count = read32(data + 16);
    uint32_t records_start = read32(data + 20);
    if (bits < 1024 || (bits & (bits - 1)) || count > INT32_MAX) return -1;
    uint64_t offsets_start = 88u + (uint64_t)bits / 8u;
    uint64_t expected = offsets_start + ((uint64_t)count + 1u) * 4u;
    if (expected != records_start || expected > length) return -1;
    const char *host = (*env)->GetStringUTFChars(env, hostname, NULL);
    if (!host) return -1;
    int result = 0;
    for (const char *suffix = host; *suffix;) {
        size_t size = strlen(suffix);
        if (maybe_present(data + 88, bits, (const uint8_t *)suffix, size)) {
            int mask = lookup(data, length, count, (uint32_t)offsets_start,
                              records_start, (const uint8_t *)suffix, size);
            if (mask < 0) { result = -1; break; }
            result |= mask;
        }
        const char *dot = strchr(suffix, '.');
        if (!dot) break;
        suffix = dot + 1;
    }
    (*env)->ReleaseStringUTFChars(env, hostname, host);
    return result;
}

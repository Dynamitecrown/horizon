package dev.horizonlab.bridge.host.mc;

import java.util.LinkedHashMap;
import java.util.Map;

/** Saved block entity data as the bridge keeps it: the type id and the fields in write order. */
public class CompoundTag {
    public final String id;
    public final Map<String, Object> values = new LinkedHashMap<>();

    public CompoundTag(String id) { this.id = id; }

    /** {"id":"...","key":value,...}: what Horizon stores for a bridged block entity. */
    public String toJson() {
        var json = new StringBuilder("{\"id\":\"").append(id).append('"');
        values.forEach((key, value) -> json.append(",\"").append(key).append("\":").append(value));
        return json.append('}').toString();
    }

    /** Reads {@link #toJson()} back; values are ints, longs (suffix L) or booleans. */
    public static CompoundTag fromJson(String json) {
        String body = json.substring(1, json.length() - 1);
        CompoundTag tag = null;
        for (String part : body.split(",")) {
            String[] pair = part.split(":", 2);
            String key = pair[0].substring(1, pair[0].length() - 1);
            String value = pair[1];
            if (key.equals("id")) {
                tag = new CompoundTag(value.substring(1, value.length() - 1));
            } else if (tag == null) {
                throw new IllegalArgumentException("Saved data without an id first: " + json);
            } else if (value.equals("true") || value.equals("false")) {
                tag.values.put(key, Boolean.parseBoolean(value));
            } else if (value.endsWith("L")) {
                tag.values.put(key, new LongValue(Long.parseLong(value.substring(0, value.length() - 1))));
            } else {
                tag.values.put(key, Integer.parseInt(value));
            }
        }
        if (tag == null) throw new IllegalArgumentException("Saved data without an id: " + json);
        return tag;
    }

    /** A long, written with an L so it reads back as a long. */
    public record LongValue(long value) {
        @Override
        public String toString() { return value + "L"; }
    }

    final class Output implements ValueOutput {
        @Override
        public void putInt(String key, int value) { values.put(key, value); }

        @Override
        public void putLong(String key, long value) { values.put(key, new LongValue(value)); }

        @Override
        public void putBoolean(String key, boolean value) { values.put(key, value); }
    }

    final class Input implements ValueInput {
        @Override
        public int getIntOr(String key, int fallback) { return values.get(key) instanceof Integer i ? i : fallback; }

        @Override
        public long getLongOr(String key, long fallback) {
            return values.get(key) instanceof LongValue l ? l.value() : fallback;
        }

        @Override
        public boolean getBooleanOr(String key, boolean fallback) {
            return values.get(key) instanceof Boolean b ? b : fallback;
        }
    }
}

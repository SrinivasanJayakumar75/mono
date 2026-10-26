package com.monotron.support.crm;

/**
 * A single writable field of a CRM resource.
 *
 * @param key      camelCase name used by the API
 * @param column   snake_case database column
 * @param type     how the value is coerced before binding
 * @param required whether the field must be present on create
 */
public record CrmField(String key, String column, Type type, boolean required) {

    public enum Type {
        TEXT, INT, DECIMAL, BOOL, DATE, TIMESTAMP
    }

    public static CrmField text(String key, String column) {
        return new CrmField(key, column, Type.TEXT, false);
    }

    public static CrmField required(String key, String column) {
        return new CrmField(key, column, Type.TEXT, true);
    }

    public static CrmField integer(String key, String column) {
        return new CrmField(key, column, Type.INT, false);
    }

    public static CrmField decimal(String key, String column) {
        return new CrmField(key, column, Type.DECIMAL, false);
    }

    public static CrmField bool(String key, String column) {
        return new CrmField(key, column, Type.BOOL, false);
    }

    public static CrmField date(String key, String column) {
        return new CrmField(key, column, Type.DATE, false);
    }

    public static CrmField timestamp(String key, String column) {
        return new CrmField(key, column, Type.TIMESTAMP, false);
    }

    public static CrmField requiredTimestamp(String key, String column) {
        return new CrmField(key, column, Type.TIMESTAMP, true);
    }
}

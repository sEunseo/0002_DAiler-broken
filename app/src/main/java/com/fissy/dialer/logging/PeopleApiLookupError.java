// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: packages/apps/Dialer/java/com/fissy/dialer/logging/people_api_lookup_error.proto

package com.fissy.dialer.logging;

/**
 * Protobuf type {@code com.fissy.dialer.logging.PeopleApiLookupError}
 */
public final class PeopleApiLookupError extends
        com.google.protobuf.GeneratedMessageLite<
                PeopleApiLookupError, PeopleApiLookupError.Builder> implements
        // @@protoc_insertion_point(message_implements:com.fissy.dialer.logging.PeopleApiLookupError)
        PeopleApiLookupErrorOrBuilder {
    // @@protoc_insertion_point(class_scope:com.fissy.dialer.logging.PeopleApiLookupError)
    private static final com.fissy.dialer.logging.PeopleApiLookupError DEFAULT_INSTANCE;
    private static volatile com.google.protobuf.Parser<PeopleApiLookupError> PARSER;

    static {
        DEFAULT_INSTANCE = new PeopleApiLookupError();
        DEFAULT_INSTANCE.makeImmutable();
    }

    private PeopleApiLookupError() {
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(
            com.google.protobuf.ByteString data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(
            com.google.protobuf.ByteString data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data, extensionRegistry);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(byte[] data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(
            byte[] data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data, extensionRegistry);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(java.io.InputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseDelimitedFrom(java.io.InputStream input)
            throws java.io.IOException {
        return parseDelimitedFrom(DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseDelimitedFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(
            com.google.protobuf.CodedInputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError parseFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static Builder newBuilder() {
        return DEFAULT_INSTANCE.toBuilder();
    }

    public static Builder newBuilder(com.fissy.dialer.logging.PeopleApiLookupError prototype) {
        return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }

    public static com.fissy.dialer.logging.PeopleApiLookupError getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public static com.google.protobuf.Parser<PeopleApiLookupError> parser() {
        return DEFAULT_INSTANCE.getParserForType();
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
            throws java.io.IOException {
        unknownFields.writeTo(output);
    }

    public int getSerializedSize() {
        int size = memoizedSerializedSize;
        if (size != -1) return size;

        size = 0;
        size += unknownFields.getSerializedSize();
        memoizedSerializedSize = size;
        return size;
    }

    protected Object dynamicMethod(
            com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
            Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE: {
                return new com.fissy.dialer.logging.PeopleApiLookupError();
            }
            case IS_INITIALIZED: {
                return DEFAULT_INSTANCE;
            }
            case MAKE_IMMUTABLE: {
                return null;
            }
            case NEW_BUILDER: {
                return new Builder();
            }
            case VISIT: {
                Visitor visitor = (Visitor) arg0;
                com.fissy.dialer.logging.PeopleApiLookupError other = (com.fissy.dialer.logging.PeopleApiLookupError) arg1;
                return this;
            }
            case MERGE_FROM_STREAM: {
                com.google.protobuf.CodedInputStream input =
                        (com.google.protobuf.CodedInputStream) arg0;
                com.google.protobuf.ExtensionRegistryLite extensionRegistry =
                        (com.google.protobuf.ExtensionRegistryLite) arg1;
                try {
                    boolean done = false;
                    while (!done) {
                        int tag = input.readTag();
                        if (tag == 0) {
                            done = true;
                        } else {
                            if (!parseUnknownField(tag, input)) {
                                done = true;
                            }
                        }
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new RuntimeException(e.setUnfinishedMessage(this));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(
                            new com.google.protobuf.InvalidProtocolBufferException(
                                    e.getMessage()).setUnfinishedMessage(this));
                }
            }
            case GET_DEFAULT_INSTANCE: {
                return DEFAULT_INSTANCE;
            }
            case GET_PARSER: {
                if (PARSER == null) {
                    synchronized (com.fissy.dialer.logging.PeopleApiLookupError.class) {
                        if (PARSER == null) {
                            PARSER = new DefaultInstanceBasedParser(DEFAULT_INSTANCE);
                        }
                    }
                }
                return PARSER;
            }
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Protobuf enum {@code com.fissy.dialer.logging.PeopleApiLookupError.Type}
     */
    public enum Type
            implements com.google.protobuf.Internal.EnumLite {
        /**
         * <code>UNKNOWN = 0;</code>
         */
        UNKNOWN(0),
        /**
         * <code>HTTP_RESPONSE_ERROR = 1;</code>
         */
        HTTP_RESPONSE_ERROR(1),
        /**
         * <code>WRONG_KIND_VALUE = 2;</code>
         */
        WRONG_KIND_VALUE(2),
        /**
         * <code>NO_ITEM_FOUND = 3;</code>
         */
        NO_ITEM_FOUND(3),
        /**
         * <code>JSON_PARSING_ERROR = 4;</code>
         */
        JSON_PARSING_ERROR(4),
        ;

        /**
         * <code>UNKNOWN = 0;</code>
         */
        public static final int UNKNOWN_VALUE = 0;
        /**
         * <code>HTTP_RESPONSE_ERROR = 1;</code>
         */
        public static final int HTTP_RESPONSE_ERROR_VALUE = 1;
        /**
         * <code>WRONG_KIND_VALUE = 2;</code>
         */
        public static final int WRONG_KIND_VALUE_VALUE = 2;
        /**
         * <code>NO_ITEM_FOUND = 3;</code>
         */
        public static final int NO_ITEM_FOUND_VALUE = 3;
        /**
         * <code>JSON_PARSING_ERROR = 4;</code>
         */
        public static final int JSON_PARSING_ERROR_VALUE = 4;
        private static final com.google.protobuf.Internal.EnumLiteMap<
                Type> internalValueMap =
                number -> Type.forNumber(number);
        private final int value;

        Type(int value) {
            this.value = value;
        }

        /**
         * @deprecated Use {@link #forNumber(int)} instead.
         */
        @java.lang.Deprecated
        public static Type valueOf(int value) {
            return forNumber(value);
        }

        public static Type forNumber(int value) {
            switch (value) {
                case 0:
                    return UNKNOWN;
                case 1:
                    return HTTP_RESPONSE_ERROR;
                case 2:
                    return WRONG_KIND_VALUE;
                case 3:
                    return NO_ITEM_FOUND;
                case 4:
                    return JSON_PARSING_ERROR;
                default:
                    return null;
            }
        }

        public static com.google.protobuf.Internal.EnumLiteMap<Type>
        internalGetValueMap() {
            return internalValueMap;
        }

        public final int getNumber() {
            return value;
        }

        // @@protoc_insertion_point(enum_scope:com.fissy.dialer.logging.PeopleApiLookupError.Type)
    }

    /**
     * Protobuf type {@code com.fissy.dialer.logging.PeopleApiLookupError}
     */
    public static final class Builder extends
            com.google.protobuf.GeneratedMessageLite.Builder<
                    com.fissy.dialer.logging.PeopleApiLookupError, Builder> implements
            // @@protoc_insertion_point(builder_implements:com.fissy.dialer.logging.PeopleApiLookupError)
            com.fissy.dialer.logging.PeopleApiLookupErrorOrBuilder {
        // Construct using com.fissy.dialer.logging.PeopleApiLookupError.newBuilder()
        private Builder() {
            super(DEFAULT_INSTANCE);
        }


        // @@protoc_insertion_point(builder_scope:com.fissy.dialer.logging.PeopleApiLookupError)
    }
}


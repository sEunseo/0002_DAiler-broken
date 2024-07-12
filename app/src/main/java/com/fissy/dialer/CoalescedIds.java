// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: packages/apps/Dialer/java/com/fissy/dialer/calllog/database/contract/coalesced_ids.proto

package com.fissy.dialer;

/**
 * <pre>
 * A proto containing a list of IDs of the rows in AnnotatedCallLog that are
 * coalesced into a row in CoalescedAnnotatedCallLog.
 * For example, if rows in the AnnotatedCallLog with IDs 123, 124, 125 are
 * coalesced into one row, the list in the proto will be [123, 124, 125].
 * </pre>
 * <p>
 * Protobuf type {@code com.fissy.dialer.CoalescedIds}
 */
public final class CoalescedIds extends
        com.google.protobuf.GeneratedMessageLite<
                CoalescedIds, CoalescedIds.Builder> implements
        // @@protoc_insertion_point(message_implements:com.fissy.dialer.CoalescedIds)
        CoalescedIdsOrBuilder {
    public static final int COALESCED_ID_FIELD_NUMBER = 1;
    // @@protoc_insertion_point(class_scope:com.fissy.dialer.CoalescedIds)
    private static final com.fissy.dialer.CoalescedIds DEFAULT_INSTANCE;
    private static volatile com.google.protobuf.Parser<CoalescedIds> PARSER;

    static {
        DEFAULT_INSTANCE = new CoalescedIds();
        DEFAULT_INSTANCE.makeImmutable();
    }

    private com.google.protobuf.Internal.LongList coalescedId_;

    private CoalescedIds() {
        coalescedId_ = emptyLongList();
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(
            com.google.protobuf.ByteString data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data);
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(
            com.google.protobuf.ByteString data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data, extensionRegistry);
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(byte[] data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data);
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(
            byte[] data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data, extensionRegistry);
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(java.io.InputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static com.fissy.dialer.CoalescedIds parseDelimitedFrom(java.io.InputStream input)
            throws java.io.IOException {
        return parseDelimitedFrom(DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.CoalescedIds parseDelimitedFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(
            com.google.protobuf.CodedInputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.CoalescedIds parseFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static Builder newBuilder() {
        return DEFAULT_INSTANCE.toBuilder();
    }

    public static Builder newBuilder(com.fissy.dialer.CoalescedIds prototype) {
        return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }

    public static com.fissy.dialer.CoalescedIds getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public static com.google.protobuf.Parser<CoalescedIds> parser() {
        return DEFAULT_INSTANCE.getParserForType();
    }

    /**
     * <code>repeated int64 coalesced_id = 1;</code>
     */
    public java.util.List<java.lang.Long>
    getCoalescedIdList() {
        return coalescedId_;
    }

    /**
     * <code>repeated int64 coalesced_id = 1;</code>
     */
    public int getCoalescedIdCount() {
        return coalescedId_.size();
    }

    /**
     * <code>repeated int64 coalesced_id = 1;</code>
     */
    public long getCoalescedId(int index) {
        return coalescedId_.getLong(index);
    }

    private void ensureCoalescedIdIsMutable() {
        if (!coalescedId_.isModifiable()) {
            coalescedId_ =
                    com.google.protobuf.GeneratedMessageLite.mutableCopy(coalescedId_);
        }
    }

    /**
     * <code>repeated int64 coalesced_id = 1;</code>
     */
    private void setCoalescedId(
            int index, long value) {
        ensureCoalescedIdIsMutable();
        coalescedId_.setLong(index, value);
    }

    /**
     * <code>repeated int64 coalesced_id = 1;</code>
     */
    private void addCoalescedId(long value) {
        ensureCoalescedIdIsMutable();
        coalescedId_.addLong(value);
    }

    /**
     * <code>repeated int64 coalesced_id = 1;</code>
     */
    private void addAllCoalescedId(
            java.lang.Iterable<? extends java.lang.Long> values) {
        ensureCoalescedIdIsMutable();
        com.google.protobuf.AbstractMessageLite.addAll(
                values, coalescedId_);
    }

    /**
     * <code>repeated int64 coalesced_id = 1;</code>
     */
    private void clearCoalescedId() {
        coalescedId_ = emptyLongList();
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
            throws java.io.IOException {
        for (int i = 0; i < coalescedId_.size(); i++) {
            output.writeInt64(1, coalescedId_.getLong(i));
        }
        unknownFields.writeTo(output);
    }

    public int getSerializedSize() {
        int size = memoizedSerializedSize;
        if (size != -1) return size;

        size = 0;
        {
            int dataSize = 0;
            for (int i = 0; i < coalescedId_.size(); i++) {
                dataSize += com.google.protobuf.CodedOutputStream
                        .computeInt64SizeNoTag(coalescedId_.getLong(i));
            }
            size += dataSize;
            size += getCoalescedIdList().size();
        }
        size += unknownFields.getSerializedSize();
        memoizedSerializedSize = size;
        return size;
    }

    protected Object dynamicMethod(
            com.google.protobuf.GeneratedMessageLite.MethodToInvoke method,
            Object arg0, Object arg1) {
        switch (method) {
            case NEW_MUTABLE_INSTANCE: {
                return new com.fissy.dialer.CoalescedIds();
            }
            case IS_INITIALIZED: {
                return DEFAULT_INSTANCE;
            }
            case MAKE_IMMUTABLE: {
                coalescedId_.makeImmutable();
                return null;
            }
            case NEW_BUILDER: {
                return new Builder();
            }
            case VISIT: {
                Visitor visitor = (Visitor) arg0;
                com.fissy.dialer.CoalescedIds other = (com.fissy.dialer.CoalescedIds) arg1;
                coalescedId_ = visitor.visitLongList(coalescedId_, other.coalescedId_);
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
                        switch (tag) {
                            case 0:
                                done = true;
                                break;
                            default: {
                                if (!parseUnknownField(tag, input)) {
                                    done = true;
                                }
                                break;
                            }
                            case 8: {
                                if (!coalescedId_.isModifiable()) {
                                    coalescedId_ =
                                            com.google.protobuf.GeneratedMessageLite.mutableCopy(coalescedId_);
                                }
                                coalescedId_.addLong(input.readInt64());
                                break;
                            }
                            case 10: {
                                int length = input.readRawVarint32();
                                int limit = input.pushLimit(length);
                                if (!coalescedId_.isModifiable() && input.getBytesUntilLimit() > 0) {
                                    coalescedId_ =
                                            com.google.protobuf.GeneratedMessageLite.mutableCopy(coalescedId_);
                                }
                                while (input.getBytesUntilLimit() > 0) {
                                    coalescedId_.addLong(input.readInt64());
                                }
                                input.popLimit(limit);
                                break;
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
                    synchronized (com.fissy.dialer.CoalescedIds.class) {
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
     * <pre>
     * A proto containing a list of IDs of the rows in AnnotatedCallLog that are
     * coalesced into a row in CoalescedAnnotatedCallLog.
     * For example, if rows in the AnnotatedCallLog with IDs 123, 124, 125 are
     * coalesced into one row, the list in the proto will be [123, 124, 125].
     * </pre>
     * <p>
     * Protobuf type {@code com.fissy.dialer.CoalescedIds}
     */
    public static final class Builder extends
            com.google.protobuf.GeneratedMessageLite.Builder<
                    com.fissy.dialer.CoalescedIds, Builder> implements
            // @@protoc_insertion_point(builder_implements:com.fissy.dialer.CoalescedIds)
            com.fissy.dialer.CoalescedIdsOrBuilder {
        // Construct using com.fissy.dialer.CoalescedIds.newBuilder()
        private Builder() {
            super(DEFAULT_INSTANCE);
        }


        /**
         * <code>repeated int64 coalesced_id = 1;</code>
         */
        public java.util.List<java.lang.Long>
        getCoalescedIdList() {
            return java.util.Collections.unmodifiableList(
                    instance.getCoalescedIdList());
        }

        /**
         * <code>repeated int64 coalesced_id = 1;</code>
         */
        public int getCoalescedIdCount() {
            return instance.getCoalescedIdCount();
        }

        /**
         * <code>repeated int64 coalesced_id = 1;</code>
         */
        public long getCoalescedId(int index) {
            return instance.getCoalescedId(index);
        }

        /**
         * <code>repeated int64 coalesced_id = 1;</code>
         */
        public Builder setCoalescedId(
                int index, long value) {
            copyOnWrite();
            instance.setCoalescedId(index, value);
            return this;
        }

        /**
         * <code>repeated int64 coalesced_id = 1;</code>
         */
        public Builder addCoalescedId(long value) {
            copyOnWrite();
            instance.addCoalescedId(value);
            return this;
        }

        /**
         * <code>repeated int64 coalesced_id = 1;</code>
         */
        public Builder addAllCoalescedId(
                java.lang.Iterable<? extends java.lang.Long> values) {
            copyOnWrite();
            instance.addAllCoalescedId(values);
            return this;
        }

        /**
         * <code>repeated int64 coalesced_id = 1;</code>
         */
        public Builder clearCoalescedId() {
            copyOnWrite();
            instance.clearCoalescedId();
            return this;
        }

        // @@protoc_insertion_point(builder_scope:com.fissy.dialer.CoalescedIds)
    }
}


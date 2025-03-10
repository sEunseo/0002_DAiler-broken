// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: packages/apps/Dialer/java/com/fissy/dialer/logging/interaction_event.proto

package com.fissy.dialer.logging;

/**
 * Protobuf type {@code com.fissy.dialer.logging.InteractionEvent}
 */
public final class InteractionEvent extends
        com.google.protobuf.GeneratedMessageLite<
                InteractionEvent, InteractionEvent.Builder> implements
        // @@protoc_insertion_point(message_implements:com.fissy.dialer.logging.InteractionEvent)
        InteractionEventOrBuilder {
    // @@protoc_insertion_point(class_scope:com.fissy.dialer.logging.InteractionEvent)
    private static final com.fissy.dialer.logging.InteractionEvent DEFAULT_INSTANCE;
    private static volatile com.google.protobuf.Parser<InteractionEvent> PARSER;

    static {
        DEFAULT_INSTANCE = new InteractionEvent();
        DEFAULT_INSTANCE.makeImmutable();
    }

    private InteractionEvent() {
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(
            com.google.protobuf.ByteString data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(
            com.google.protobuf.ByteString data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data, extensionRegistry);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(byte[] data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(
            byte[] data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, data, extensionRegistry);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(java.io.InputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseDelimitedFrom(java.io.InputStream input)
            throws java.io.IOException {
        return parseDelimitedFrom(DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseDelimitedFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(
            com.google.protobuf.CodedInputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input);
    }

    public static com.fissy.dialer.logging.InteractionEvent parseFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageLite.parseFrom(
                DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static Builder newBuilder() {
        return DEFAULT_INSTANCE.toBuilder();
    }

    public static Builder newBuilder(com.fissy.dialer.logging.InteractionEvent prototype) {
        return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }

    public static com.fissy.dialer.logging.InteractionEvent getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public static com.google.protobuf.Parser<InteractionEvent> parser() {
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
                return new com.fissy.dialer.logging.InteractionEvent();
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
                com.fissy.dialer.logging.InteractionEvent other = (com.fissy.dialer.logging.InteractionEvent) arg1;
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
                    synchronized (com.fissy.dialer.logging.InteractionEvent.class) {
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
     * Next Tag: 38
     * </pre>
     * <p>
     * Protobuf enum {@code com.fissy.dialer.logging.InteractionEvent.Type}
     */
    public enum Type
            implements com.google.protobuf.Internal.EnumLite {
        /**
         * <code>UNKNOWN = 0;</code>
         */
        UNKNOWN(0),
        /**
         * <pre>
         * An incoming call was blocked
         * </pre>
         *
         * <code>CALL_BLOCKED = 15;</code>
         */
        CALL_BLOCKED(15),
        /**
         * <pre>
         * The user blocked a number from the Call Log screen
         * </pre>
         *
         * <code>BLOCK_NUMBER_CALL_LOG = 16;</code>
         */
        BLOCK_NUMBER_CALL_LOG(16),
        /**
         * <pre>
         * The user blocked a number from the Call details screen
         * </pre>
         *
         * <code>BLOCK_NUMBER_CALL_DETAIL = 17;</code>
         */
        BLOCK_NUMBER_CALL_DETAIL(17),
        /**
         * <pre>
         * The user blocked a number from the Management screen
         * </pre>
         *
         * <code>BLOCK_NUMBER_MANAGEMENT_SCREEN = 18;</code>
         */
        BLOCK_NUMBER_MANAGEMENT_SCREEN(18),
        /**
         * <pre>
         * The user unblocked a number from the Call Log screen
         * </pre>
         *
         * <code>UNBLOCK_NUMBER_CALL_LOG = 19;</code>
         */
        UNBLOCK_NUMBER_CALL_LOG(19),
        /**
         * <pre>
         * The user unblocked a number from the Call details screen
         * </pre>
         *
         * <code>UNBLOCK_NUMBER_CALL_DETAIL = 20;</code>
         */
        UNBLOCK_NUMBER_CALL_DETAIL(20),
        /**
         * <pre>
         * The user unblocked a number from the Management screen
         * </pre>
         *
         * <code>UNBLOCK_NUMBER_MANAGEMENT_SCREEN = 21;</code>
         */
        UNBLOCK_NUMBER_MANAGEMENT_SCREEN(21),
        /**
         * <pre>
         * The user blocked numbers from contacts marked as send to voicemail
         * </pre>
         *
         * <code>IMPORT_SEND_TO_VOICEMAIL = 22;</code>
         */
        IMPORT_SEND_TO_VOICEMAIL(22),
        /**
         * <pre>
         * The user blocked a number then undid the block
         * </pre>
         *
         * <code>UNDO_BLOCK_NUMBER = 23;</code>
         */
        UNDO_BLOCK_NUMBER(23),
        /**
         * <pre>
         * The user unblocked a number then undid the unblock
         * </pre>
         *
         * <code>UNDO_UNBLOCK_NUMBER = 24;</code>
         */
        UNDO_UNBLOCK_NUMBER(24),
        /**
         * <pre>
         * Actions in speed dial
         * </pre>
         *
         * <code>SPEED_DIAL_PIN_CONTACT = 25;</code>
         */
        SPEED_DIAL_PIN_CONTACT(25),
        /**
         * <code>SPEED_DIAL_REMOVE_CONTACT = 26;</code>
         */
        SPEED_DIAL_REMOVE_CONTACT(26),
        /**
         * <code>SPEED_DIAL_OPEN_CONTACT_CARD = 27;</code>
         */
        SPEED_DIAL_OPEN_CONTACT_CARD(27),
        /**
         * <code>SPEED_DIAL_CLICK_CONTACT_WITH_AMBIGUOUS_NUMBER = 28;</code>
         */
        SPEED_DIAL_CLICK_CONTACT_WITH_AMBIGUOUS_NUMBER(28),
        /**
         * <code>SPEED_DIAL_SET_DEFAULT_NUMBER_FOR_AMBIGUOUS_CONTACT = 29;</code>
         */
        SPEED_DIAL_SET_DEFAULT_NUMBER_FOR_AMBIGUOUS_CONTACT(29),
        /**
         * <pre>
         * Open quick contact from where
         * </pre>
         *
         * <code>OPEN_QUICK_CONTACT_FROM_CALL_LOG = 30;</code>
         */
        OPEN_QUICK_CONTACT_FROM_CALL_LOG(30),
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CALL_DETAILS = 31;</code>
         */
        OPEN_QUICK_CONTACT_FROM_CALL_DETAILS(31),
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_ALL_CONTACTS_GENERAL = 32;</code>
         */
        OPEN_QUICK_CONTACT_FROM_ALL_CONTACTS_GENERAL(32),
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_BADGE = 33;</code>
         */
        OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_BADGE(33),
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_ITEM = 34;</code>
         */
        OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_ITEM(34),
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_SEARCH = 35;</code>
         */
        OPEN_QUICK_CONTACT_FROM_SEARCH(35),
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_VOICEMAIL = 36;</code>
         */
        OPEN_QUICK_CONTACT_FROM_VOICEMAIL(36),
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CALL_HISTORY = 37;</code>
         */
        OPEN_QUICK_CONTACT_FROM_CALL_HISTORY(37),
        ;

        /**
         * <code>UNKNOWN = 0;</code>
         */
        public static final int UNKNOWN_VALUE = 0;
        /**
         * <pre>
         * An incoming call was blocked
         * </pre>
         *
         * <code>CALL_BLOCKED = 15;</code>
         */
        public static final int CALL_BLOCKED_VALUE = 15;
        /**
         * <pre>
         * The user blocked a number from the Call Log screen
         * </pre>
         *
         * <code>BLOCK_NUMBER_CALL_LOG = 16;</code>
         */
        public static final int BLOCK_NUMBER_CALL_LOG_VALUE = 16;
        /**
         * <pre>
         * The user blocked a number from the Call details screen
         * </pre>
         *
         * <code>BLOCK_NUMBER_CALL_DETAIL = 17;</code>
         */
        public static final int BLOCK_NUMBER_CALL_DETAIL_VALUE = 17;
        /**
         * <pre>
         * The user blocked a number from the Management screen
         * </pre>
         *
         * <code>BLOCK_NUMBER_MANAGEMENT_SCREEN = 18;</code>
         */
        public static final int BLOCK_NUMBER_MANAGEMENT_SCREEN_VALUE = 18;
        /**
         * <pre>
         * The user unblocked a number from the Call Log screen
         * </pre>
         *
         * <code>UNBLOCK_NUMBER_CALL_LOG = 19;</code>
         */
        public static final int UNBLOCK_NUMBER_CALL_LOG_VALUE = 19;
        /**
         * <pre>
         * The user unblocked a number from the Call details screen
         * </pre>
         *
         * <code>UNBLOCK_NUMBER_CALL_DETAIL = 20;</code>
         */
        public static final int UNBLOCK_NUMBER_CALL_DETAIL_VALUE = 20;
        /**
         * <pre>
         * The user unblocked a number from the Management screen
         * </pre>
         *
         * <code>UNBLOCK_NUMBER_MANAGEMENT_SCREEN = 21;</code>
         */
        public static final int UNBLOCK_NUMBER_MANAGEMENT_SCREEN_VALUE = 21;
        /**
         * <pre>
         * The user blocked numbers from contacts marked as send to voicemail
         * </pre>
         *
         * <code>IMPORT_SEND_TO_VOICEMAIL = 22;</code>
         */
        public static final int IMPORT_SEND_TO_VOICEMAIL_VALUE = 22;
        /**
         * <pre>
         * The user blocked a number then undid the block
         * </pre>
         *
         * <code>UNDO_BLOCK_NUMBER = 23;</code>
         */
        public static final int UNDO_BLOCK_NUMBER_VALUE = 23;
        /**
         * <pre>
         * The user unblocked a number then undid the unblock
         * </pre>
         *
         * <code>UNDO_UNBLOCK_NUMBER = 24;</code>
         */
        public static final int UNDO_UNBLOCK_NUMBER_VALUE = 24;
        /**
         * <pre>
         * Actions in speed dial
         * </pre>
         *
         * <code>SPEED_DIAL_PIN_CONTACT = 25;</code>
         */
        public static final int SPEED_DIAL_PIN_CONTACT_VALUE = 25;
        /**
         * <code>SPEED_DIAL_REMOVE_CONTACT = 26;</code>
         */
        public static final int SPEED_DIAL_REMOVE_CONTACT_VALUE = 26;
        /**
         * <code>SPEED_DIAL_OPEN_CONTACT_CARD = 27;</code>
         */
        public static final int SPEED_DIAL_OPEN_CONTACT_CARD_VALUE = 27;
        /**
         * <code>SPEED_DIAL_CLICK_CONTACT_WITH_AMBIGUOUS_NUMBER = 28;</code>
         */
        public static final int SPEED_DIAL_CLICK_CONTACT_WITH_AMBIGUOUS_NUMBER_VALUE = 28;
        /**
         * <code>SPEED_DIAL_SET_DEFAULT_NUMBER_FOR_AMBIGUOUS_CONTACT = 29;</code>
         */
        public static final int SPEED_DIAL_SET_DEFAULT_NUMBER_FOR_AMBIGUOUS_CONTACT_VALUE = 29;
        /**
         * <pre>
         * Open quick contact from where
         * </pre>
         *
         * <code>OPEN_QUICK_CONTACT_FROM_CALL_LOG = 30;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_CALL_LOG_VALUE = 30;
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CALL_DETAILS = 31;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_CALL_DETAILS_VALUE = 31;
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_ALL_CONTACTS_GENERAL = 32;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_ALL_CONTACTS_GENERAL_VALUE = 32;
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_BADGE = 33;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_BADGE_VALUE = 33;
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_ITEM = 34;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_ITEM_VALUE = 34;
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_SEARCH = 35;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_SEARCH_VALUE = 35;
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_VOICEMAIL = 36;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_VOICEMAIL_VALUE = 36;
        /**
         * <code>OPEN_QUICK_CONTACT_FROM_CALL_HISTORY = 37;</code>
         */
        public static final int OPEN_QUICK_CONTACT_FROM_CALL_HISTORY_VALUE = 37;
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
                case 15:
                    return CALL_BLOCKED;
                case 16:
                    return BLOCK_NUMBER_CALL_LOG;
                case 17:
                    return BLOCK_NUMBER_CALL_DETAIL;
                case 18:
                    return BLOCK_NUMBER_MANAGEMENT_SCREEN;
                case 19:
                    return UNBLOCK_NUMBER_CALL_LOG;
                case 20:
                    return UNBLOCK_NUMBER_CALL_DETAIL;
                case 21:
                    return UNBLOCK_NUMBER_MANAGEMENT_SCREEN;
                case 22:
                    return IMPORT_SEND_TO_VOICEMAIL;
                case 23:
                    return UNDO_BLOCK_NUMBER;
                case 24:
                    return UNDO_UNBLOCK_NUMBER;
                case 25:
                    return SPEED_DIAL_PIN_CONTACT;
                case 26:
                    return SPEED_DIAL_REMOVE_CONTACT;
                case 27:
                    return SPEED_DIAL_OPEN_CONTACT_CARD;
                case 28:
                    return SPEED_DIAL_CLICK_CONTACT_WITH_AMBIGUOUS_NUMBER;
                case 29:
                    return SPEED_DIAL_SET_DEFAULT_NUMBER_FOR_AMBIGUOUS_CONTACT;
                case 30:
                    return OPEN_QUICK_CONTACT_FROM_CALL_LOG;
                case 31:
                    return OPEN_QUICK_CONTACT_FROM_CALL_DETAILS;
                case 32:
                    return OPEN_QUICK_CONTACT_FROM_ALL_CONTACTS_GENERAL;
                case 33:
                    return OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_BADGE;
                case 34:
                    return OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_ITEM;
                case 35:
                    return OPEN_QUICK_CONTACT_FROM_SEARCH;
                case 36:
                    return OPEN_QUICK_CONTACT_FROM_VOICEMAIL;
                case 37:
                    return OPEN_QUICK_CONTACT_FROM_CALL_HISTORY;
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

        // @@protoc_insertion_point(enum_scope:com.fissy.dialer.logging.InteractionEvent.Type)
    }

    /**
     * Protobuf type {@code com.fissy.dialer.logging.InteractionEvent}
     */
    public static final class Builder extends
            com.google.protobuf.GeneratedMessageLite.Builder<
                    com.fissy.dialer.logging.InteractionEvent, Builder> implements
            // @@protoc_insertion_point(builder_implements:com.fissy.dialer.logging.InteractionEvent)
            com.fissy.dialer.logging.InteractionEventOrBuilder {
        // Construct using com.fissy.dialer.logging.InteractionEvent.newBuilder()
        private Builder() {
            super(DEFAULT_INSTANCE);
        }


        // @@protoc_insertion_point(builder_scope:com.fissy.dialer.logging.InteractionEvent)
    }
}


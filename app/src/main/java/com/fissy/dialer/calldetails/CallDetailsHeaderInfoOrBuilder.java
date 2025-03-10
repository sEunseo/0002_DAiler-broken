// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: packages/apps/Dialer/java/com/fissy/dialer/calldetails/proto/call_details_header_info.proto

package com.fissy.dialer.calldetails;

public interface CallDetailsHeaderInfoOrBuilder extends
        // @@protoc_insertion_point(interface_extends:com.fissy.dialer.calldetails.CallDetailsHeaderInfo)
        com.google.protobuf.MessageLiteOrBuilder {

    /**
     * <pre>
     * The number of all call detail entries.
     * </pre>
     *
     * <code>optional .com.fissy.dialer.DialerPhoneNumber dialer_phone_number = 1;</code>
     */
    boolean hasDialerPhoneNumber();

    /**
     * <pre>
     * The number of all call detail entries.
     * </pre>
     *
     * <code>optional .com.fissy.dialer.DialerPhoneNumber dialer_phone_number = 1;</code>
     */
    com.fissy.dialer.DialerPhoneNumber getDialerPhoneNumber();

    /**
     * <pre>
     * Information used to load the contact photo.
     * </pre>
     *
     * <code>optional .com.fissy.dialer.glidephotomanager.PhotoInfo photo_info = 2;</code>
     */
    boolean hasPhotoInfo();

    /**
     * <pre>
     * Information used to load the contact photo.
     * </pre>
     *
     * <code>optional .com.fissy.dialer.glidephotomanager.PhotoInfo photo_info = 2;</code>
     */
    com.fissy.dialer.glidephotomanager.PhotoInfo getPhotoInfo();

    /**
     * <pre>
     * Primary text of the header, which can be
     * (1) a presentation name (e.g., "Restricted", "Unknown", etc.),
     * (2) the contact name, or
     * (3) the formatted number.
     * </pre>
     *
     * <code>optional string primary_text = 3;</code>
     */
    boolean hasPrimaryText();

    /**
     * <pre>
     * Primary text of the header, which can be
     * (1) a presentation name (e.g., "Restricted", "Unknown", etc.),
     * (2) the contact name, or
     * (3) the formatted number.
     * </pre>
     *
     * <code>optional string primary_text = 3;</code>
     */
    java.lang.String getPrimaryText();

    /**
     * <pre>
     * Primary text of the header, which can be
     * (1) a presentation name (e.g., "Restricted", "Unknown", etc.),
     * (2) the contact name, or
     * (3) the formatted number.
     * </pre>
     *
     * <code>optional string primary_text = 3;</code>
     */
    com.google.protobuf.ByteString
    getPrimaryTextBytes();

    /**
     * <pre>
     * Secondary test of the header, which describes the number.
     * Some examples are:
     *   "Mobile • 555-1234",
     *   "Blocked • Mobile • 555-1234", and
     *   "Spam • Mobile • 555-1234".
     * </pre>
     *
     * <code>optional string secondary_text = 4;</code>
     */
    boolean hasSecondaryText();

    /**
     * <pre>
     * Secondary test of the header, which describes the number.
     * Some examples are:
     *   "Mobile • 555-1234",
     *   "Blocked • Mobile • 555-1234", and
     *   "Spam • Mobile • 555-1234".
     * </pre>
     *
     * <code>optional string secondary_text = 4;</code>
     */
    java.lang.String getSecondaryText();

    /**
     * <pre>
     * Secondary test of the header, which describes the number.
     * Some examples are:
     *   "Mobile • 555-1234",
     *   "Blocked • Mobile • 555-1234", and
     *   "Spam • Mobile • 555-1234".
     * </pre>
     *
     * <code>optional string secondary_text = 4;</code>
     */
    com.google.protobuf.ByteString
    getSecondaryTextBytes();
}

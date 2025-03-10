// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: packages/apps/Dialer/java/com/fissy/dialer/callintent/call_specific_app_data.proto

package com.fissy.dialer.callintent;

public interface CallSpecificAppDataOrBuilder extends
        // @@protoc_insertion_point(interface_extends:com.fissy.dialer.callintent.CallSpecificAppData)
        com.google.protobuf.MessageLiteOrBuilder {

    /**
     * <code>optional .com.fissy.dialer.callintent.CallInitiationType.Type call_initiation_type = 1;</code>
     */
    boolean hasCallInitiationType();

    /**
     * <code>optional .com.fissy.dialer.callintent.CallInitiationType.Type call_initiation_type = 1;</code>
     */
    com.fissy.dialer.callintent.CallInitiationType.Type getCallInitiationType();

    /**
     * <code>optional int32 position_of_selected_search_result = 2;</code>
     */
    boolean hasPositionOfSelectedSearchResult();

    /**
     * <code>optional int32 position_of_selected_search_result = 2;</code>
     */
    int getPositionOfSelectedSearchResult();

    /**
     * <code>optional int32 characters_in_search_string = 3;</code>
     */
    boolean hasCharactersInSearchString();

    /**
     * <code>optional int32 characters_in_search_string = 3;</code>
     */
    int getCharactersInSearchString();

    /**
     * <code>repeated .com.fissy.dialer.callintent.SpeedDialContactType.Type speed_dial_contact_type = 4;</code>
     */
    java.util.List<com.fissy.dialer.callintent.SpeedDialContactType.Type> getSpeedDialContactTypeList();

    /**
     * <code>repeated .com.fissy.dialer.callintent.SpeedDialContactType.Type speed_dial_contact_type = 4;</code>
     */
    int getSpeedDialContactTypeCount();

    /**
     * <code>repeated .com.fissy.dialer.callintent.SpeedDialContactType.Type speed_dial_contact_type = 4;</code>
     */
    com.fissy.dialer.callintent.SpeedDialContactType.Type getSpeedDialContactType(int index);

    /**
     * <code>optional int32 speed_dial_contact_position = 5;</code>
     */
    boolean hasSpeedDialContactPosition();

    /**
     * <code>optional int32 speed_dial_contact_position = 5;</code>
     */
    int getSpeedDialContactPosition();

    /**
     * <code>optional int64 time_since_app_launch = 6;</code>
     */
    boolean hasTimeSinceAppLaunch();

    /**
     * <code>optional int64 time_since_app_launch = 6;</code>
     */
    long getTimeSinceAppLaunch();

    /**
     * <code>optional int64 time_since_first_click = 7;</code>
     */
    boolean hasTimeSinceFirstClick();

    /**
     * <code>optional int64 time_since_first_click = 7;</code>
     */
    long getTimeSinceFirstClick();

    /**
     * <pre>
     * The following two list should be of the same length
     * (adding another message is not allowed here)
     * </pre>
     *
     * <code>repeated .com.fissy.dialer.logging.UiAction.Type ui_actions_since_app_launch = 8;</code>
     */
    java.util.List<com.fissy.dialer.logging.UiAction.Type> getUiActionsSinceAppLaunchList();

    /**
     * <pre>
     * The following two list should be of the same length
     * (adding another message is not allowed here)
     * </pre>
     *
     * <code>repeated .com.fissy.dialer.logging.UiAction.Type ui_actions_since_app_launch = 8;</code>
     */
    int getUiActionsSinceAppLaunchCount();

    /**
     * <pre>
     * The following two list should be of the same length
     * (adding another message is not allowed here)
     * </pre>
     *
     * <code>repeated .com.fissy.dialer.logging.UiAction.Type ui_actions_since_app_launch = 8;</code>
     */
    com.fissy.dialer.logging.UiAction.Type getUiActionsSinceAppLaunch(int index);

    /**
     * <code>repeated int64 ui_action_timestamps_since_app_launch = 9;</code>
     */
    java.util.List<java.lang.Long> getUiActionTimestampsSinceAppLaunchList();

    /**
     * <code>repeated int64 ui_action_timestamps_since_app_launch = 9;</code>
     */
    int getUiActionTimestampsSinceAppLaunchCount();

    /**
     * <code>repeated int64 ui_action_timestamps_since_app_launch = 9;</code>
     */
    long getUiActionTimestampsSinceAppLaunch(int index);

    /**
     * <code>optional int32 starting_tab_index = 10;</code>
     */
    boolean hasStartingTabIndex();

    /**
     * <code>optional int32 starting_tab_index = 10;</code>
     */
    int getStartingTabIndex();

    /**
     * <pre>
     * For recording the appearance of video call button
     * </pre>
     *
     * <code>optional int32 lightbringer_button_appear_in_expanded_call_log_item_count = 11;</code>
     */
    boolean hasLightbringerButtonAppearInExpandedCallLogItemCount();

    /**
     * <pre>
     * For recording the appearance of video call button
     * </pre>
     *
     * <code>optional int32 lightbringer_button_appear_in_expanded_call_log_item_count = 11;</code>
     */
    int getLightbringerButtonAppearInExpandedCallLogItemCount();

    /**
     * <code>optional int32 lightbringer_button_appear_in_collapsed_call_log_item_count = 12;</code>
     */
    boolean hasLightbringerButtonAppearInCollapsedCallLogItemCount();

    /**
     * <code>optional int32 lightbringer_button_appear_in_collapsed_call_log_item_count = 12;</code>
     */
    int getLightbringerButtonAppearInCollapsedCallLogItemCount();

    /**
     * <code>optional int32 lightbringer_button_appear_in_search_count = 13;</code>
     */
    boolean hasLightbringerButtonAppearInSearchCount();

    /**
     * <code>optional int32 lightbringer_button_appear_in_search_count = 13;</code>
     */
    int getLightbringerButtonAppearInSearchCount();

    /**
     * <pre>
     * Indicates that the call is open to modification from assisted dialing.
     * </pre>
     *
     * <code>optional bool allow_assisted_dialing = 14;</code>
     */
    boolean hasAllowAssistedDialing();

    /**
     * <pre>
     * Indicates that the call is open to modification from assisted dialing.
     * </pre>
     *
     * <code>optional bool allow_assisted_dialing = 14;</code>
     */
    boolean getAllowAssistedDialing();
}

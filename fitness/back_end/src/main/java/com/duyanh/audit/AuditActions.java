package com.duyanh.audit;

/**
 * Tên hành động ghi vào cột audit_logs.action.
 */
public final class AuditActions {

	public static final String LOGIN = "LOGIN";
	public static final String REGISTER = "REGISTER";
	public static final String EMAIL_VERIFY = "EMAIL_VERIFY";
	public static final String PASSWORD_RESET = "PASSWORD_RESET";
	public static final String PACKAGE_CREATE = "PACKAGE_CREATE";
	public static final String PACKAGE_UPDATE = "PACKAGE_UPDATE";
	public static final String SUBSCRIPTION_PURCHASE = "SUBSCRIPTION_PURCHASE";
	public static final String SUBSCRIPTION_FREEZE = "SUBSCRIPTION_FREEZE";
	public static final String SUBSCRIPTION_UNFREEZE = "SUBSCRIPTION_UNFREEZE";
	public static final String PAYMENT_CONFIRM = "PAYMENT_CONFIRM";
	public static final String PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
	public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
	public static final String BOOKING_CREATE = "BOOKING_CREATE";
	public static final String BOOKING_CANCEL = "BOOKING_CANCEL";
	public static final String SCHEDULE_UPDATE = "SCHEDULE_UPDATE";
	public static final String CLASS_CREATE = "CLASS_CREATE";
	public static final String CLASS_SESSION_CREATE = "CLASS_SESSION_CREATE";
	public static final String NOTIFICATION_BROADCAST = "NOTIFICATION_BROADCAST";
	public static final String USER_CREATE = "USER_CREATE";
	public static final String USER_UPDATE = "USER_UPDATE";
	public static final String USER_STATUS_CHANGE = "USER_STATUS_CHANGE";
	public static final String USER_ROLES_CHANGE = "USER_ROLES_CHANGE";
	public static final String CHECK_IN = "CHECK_IN";
	public static final String CHECK_OUT = "CHECK_OUT";
	public static final String EXERCISE_CREATE = "EXERCISE_CREATE";
	public static final String WORKOUT_PLAN_CREATE = "WORKOUT_PLAN_CREATE";
	public static final String WORKOUT_PLAN_UPDATE = "WORKOUT_PLAN_UPDATE";
	public static final String PROFILE_UPDATE = "PROFILE_UPDATE";
	public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
	public static final String BOOKING_ATTENDANCE = "BOOKING_ATTENDANCE";
	public static final String CLASS_SESSION_CANCEL = "CLASS_SESSION_CANCEL";
	public static final String TRAINER_PROFILE_UPDATE = "TRAINER_PROFILE_UPDATE";

	private AuditActions() {
	}
}

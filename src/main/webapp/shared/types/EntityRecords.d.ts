export type EntityName = "tasks" | "users" | "schedules";

export interface TaskRecord {
	taskId: number;
	taskTitle: string;
	taskDescription: string | null;
	taskDateCreated: string | null;
	taskDateDue: string | null;
}

export interface UserRecord {
	userId: number;
	userName: string;
	firstName: string;
	lastName: string;
	email: string;
	role: string;
}

export interface ScheduleRecord {
	notificationSettings: string;
	tasks: Record<string, TaskRecord>;
	user: UserRecord;
}

export type EntityRecord = TaskRecord | UserRecord | ScheduleRecord;
export type EntityRecordCollection =
	| EntityRecord[]
	| Record<string, EntityRecord>;

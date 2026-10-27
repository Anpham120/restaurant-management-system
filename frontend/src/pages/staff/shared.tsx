import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { Attendance, Employee, PageResponse, ShiftAssignment } from '../../api/types';
import { clock } from '../../lib/format';

export const useAssignments = (from: string, to: string, employeeId?: number | '') =>
  useQuery({
    queryKey: ['shift-assignments', from, to, employeeId ?? ''],
    queryFn: () => api.get<ShiftAssignment[]>(`/api/shift-assignments?from=${from}&to=${to}${employeeId ? `&employeeId=${employeeId}` : ''}`),
  });

export const useAttendances = (from: string, to: string) =>
  useQuery({ queryKey: ['attendances', from, to], queryFn: () => api.get<Attendance[]>(`/api/attendances?from=${from}&to=${to}`) });

export const useActiveEmployees = () =>
  useQuery({ queryKey: ['employees', 'ACTIVE', 'all'], queryFn: () => api.get<PageResponse<Employee>>('/api/employees?status=ACTIVE&size=100') });

export const span = (a: ShiftAssignment) => `${clock(a.scheduledStartAt)}–${clock(a.scheduledEndAt)}`;

export const ASSIGNMENT_STATUS: Record<string, string> = { SCHEDULED: 'Đã phân', COMPLETED: 'Đã chấm công', CANCELLED: 'Đã hủy' };

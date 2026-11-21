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
  useQuery({
    queryKey: ['employees', 'ACTIVE', 'all'],
    queryFn: async () => {
      const url = '/api/employees?status=ACTIVE&size=100';
      const first = await api.get<PageResponse<Employee>>(url);
      const employees = [...first.items];
      for (let page = 1; page * first.size < first.totalItems; page += 1) {
        const next = await api.get<PageResponse<Employee>>(`${url}&page=${page}`);
        employees.push(...next.items);
      }
      return employees;
    },
  });

export const span = (a: ShiftAssignment) => `${clock(a.scheduledStartAt)}–${clock(a.scheduledEndAt)}`;

export const ASSIGNMENT_STATUS: Record<string, string> = { SCHEDULED: 'Đã phân', COMPLETED: 'Đã chấm công', CANCELLED: 'Đã hủy' };

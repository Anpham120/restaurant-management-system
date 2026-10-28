import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { Department, Employee, PageResponse, Role, RoleInfo } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, StatusTag, Tabs, useAction } from '../../components/ui';
import { businessToday, day, roleLabel } from '../../lib/format';

const WORK: Record<string, [string, 'go' | 'wait' | 'stop']> = { ACTIVE: ['Đang làm', 'go'], INACTIVE: ['Tạm nghỉ', 'wait'], LEFT: ['Đã nghỉ việc', 'stop'] };
const ACCOUNT: Record<string, string> = { ACTIVE: 'Hoạt động', LOCKED: 'Đã khóa', DISABLED: 'Vô hiệu' };

const emptyForm = {
  employeeCode: '', fullName: '', departmentId: '' as number | '', gender: '', dateOfBirth: '', phone: '', email: '', address: '', hireDate: '',
  workStatus: 'ACTIVE', username: '', initialPassword: '', roles: [] as Role[],
};

/** Hồ sơ nhân viên, tài khoản đăng nhập, vai trò; phòng ban. */
export function EmployeesPage() {
  const me = useAuth().user;
  const [tab, setTab] = useState<'employees' | 'departments'>('employees');
  const [status, setStatus] = useState('ACTIVE');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [edit, setEdit] = useState<'new' | Employee | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [dialog, setDialog] = useState<'roles' | 'reset' | 'leave' | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);
  const [password, setPassword] = useState('');
  const [endDate, setEndDate] = useState(businessToday());
  const [deptEdit, setDeptEdit] = useState<Department | 'new' | null>(null);
  const [deptName, setDeptName] = useState('');

  const size = 20;
  const list = useQuery({ queryKey: ['employees', status, page], queryFn: () => api.get<PageResponse<Employee>>(`/api/employees?page=${page}&size=${size}${status ? `&status=${status}` : ''}`) });
  const depts = useQuery({ queryKey: ['departments'], queryFn: () => api.get<Department[]>('/api/departments') });
  const roleList = useQuery({ queryKey: ['roles'], queryFn: () => api.get<RoleInfo[]>('/api/roles') });
  const detail = useQuery({ queryKey: ['employee', selectedId], queryFn: () => api.get<Employee>(`/api/employees/${selectedId}`), enabled: selectedId != null });
  const keys = [['employees'], ['employee']];

  const save = useAction(
    () => {
      const base = {
        fullName: form.fullName.trim(), departmentId: form.departmentId, gender: form.gender || null, dateOfBirth: form.dateOfBirth || null,
        phone: form.phone.trim() || null, email: form.email.trim() || null, address: form.address.trim() || null, hireDate: form.hireDate || null,
      };
      return edit === 'new'
        ? api.post<Employee>('/api/employees', { ...base, employeeCode: form.employeeCode.trim(), username: form.username.trim(), initialPassword: form.initialPassword, roles: form.roles })
        : api.put<Employee>(`/api/employees/${(edit as Employee).id}`, { ...base, workStatus: form.workStatus });
    },
    { success: 'Đã lưu nhân viên', invalidate: keys, onSuccess: (r) => { setEdit(null); setSelectedId(r.id); } },
  );
  const acct = useAction(
    (a: 'lock' | 'unlock' | 'roles' | 'reset' | 'leave') => {
      const e = detail.data!;
      if (a === 'roles') return api.put(`/api/accounts/${e.accountId}/roles`, { roles });
      if (a === 'reset') return api.post(`/api/accounts/${e.accountId}/reset-password`, { newPassword: password });
      if (a === 'leave') return api.post(`/api/employees/${e.id}/leave`, { endDate });
      return api.post(`/api/accounts/${e.accountId}/${a}`);
    },
    {
      success: (_r, a) => ({ lock: 'Đã khóa tài khoản', unlock: 'Đã mở khóa tài khoản', roles: 'Đã cập nhật vai trò', reset: 'Đã đặt mật khẩu tạm', leave: 'Đã ghi nhận nghỉ việc' })[a],
      invalidate: keys,
      onSuccess: () => setDialog(null),
    },
  );
  const saveDept = useAction(
    () => (deptEdit === 'new' ? api.post('/api/departments', { name: deptName.trim() }) : api.put(`/api/departments/${(deptEdit as Department).id}`, { name: deptName.trim() })),
    { success: 'Đã lưu phòng ban', invalidate: [['departments'], ['employees']], onSuccess: () => setDeptEdit(null) },
  );

  function openEdit(e: Employee | 'new') {
    setForm(e === 'new' ? emptyForm : {
      ...emptyForm, employeeCode: e.employeeCode, fullName: e.fullName, departmentId: e.departmentId, gender: e.gender ?? '', dateOfBirth: e.dateOfBirth ?? '',
      phone: e.phone ?? '', email: e.email ?? '', address: e.address ?? '', hireDate: e.hireDate ?? '', workStatus: e.workStatus === 'LEFT' ? 'ACTIVE' : e.workStatus,
    });
    save.reset();
    setEdit(e);
  }
  function openDialog(d: 'roles' | 'reset' | 'leave') {
    setRoles(detail.data?.roles ?? []);
    setPassword('');
    setEndDate(businessToday());
    acct.reset();
    setDialog(d);
  }
  const toggleRole = (list: Role[], r: Role) => (list.includes(r) ? list.filter((x) => x !== r) : [...list, r]);
  const e = detail.data;
  const isMe = e?.id === me?.employeeId;
  const totalPages = list.data ? Math.max(1, Math.ceil(list.data.totalItems / size)) : 1;

  return (
    <main className="main">
      <PageHead title="Nhân viên"
                actions={tab === 'employees'
                  ? <button className="btn btn-primary" onClick={() => openEdit('new')}>Thêm nhân viên</button>
                  : <button className="btn btn-primary" onClick={() => { setDeptName(''); saveDept.reset(); setDeptEdit('new'); }}>Thêm phòng ban</button>} />
      <Tabs value={tab} onChange={setTab} options={[['employees', 'Nhân viên'], ['departments', 'Phòng ban']]} />

      {tab === 'departments' ? (
        <DataTable rows={depts.data} rowKey={(d) => d.id} empty="Chưa có phòng ban."
          columns={[
            { header: 'Phòng ban', render: (d) => <b>{d.name}</b> },
            { header: '', render: (d) => <button className="btn btn-sm" onClick={() => { setDeptName(d.name); saveDept.reset(); setDeptEdit(d); }}>Đổi tên</button> },
          ]} />
      ) : (
        <div className="split">
          <div>
            <div className="filters">
              <Field label="Tình trạng">
                <select className="input" value={status} onChange={(ev) => { setStatus(ev.target.value); setPage(0); }}>
                  <option value="">Tất cả</option><option value="ACTIVE">Đang làm</option><option value="INACTIVE">Tạm nghỉ</option><option value="LEFT">Đã nghỉ việc</option>
                </select>
              </Field>
            </div>
            <ErrorNotice error={list.error} />
            <DataTable rows={list.data?.items} rowKey={(x) => x.id} selectedKey={selectedId} onRowClick={(x) => setSelectedId(x.id)} empty="Không có nhân viên."
              columns={[
                { header: 'Mã', render: (x) => x.employeeCode },
                { header: 'Họ tên', render: (x) => <b>{x.fullName}</b> },
                { header: 'Vai trò', render: (x) => x.roles.map((r) => roleLabel[r]).join(', ') || '—' },
                { header: 'Tài khoản', render: (x) => x.username ?? '—' },
                { header: 'Tình trạng', render: (x) => <StatusTag tone={WORK[x.workStatus][1]}>{WORK[x.workStatus][0]}</StatusTag> },
              ]} />
            {totalPages > 1 && (
              <div className="toolbar" style={{ marginTop: '0.75rem' }}>
                <button className="btn btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
                <span className="muted">Trang {page + 1}/{totalPages}</span>
                <button className="btn btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
              </div>
            )}
          </div>
          <aside className="panel stack">
            {!e ? <p className="muted pick-hint">Chọn nhân viên để xem hồ sơ và tài khoản.</p> : (
              <>
                <div className="row-between"><h2>{e.fullName}</h2><StatusTag tone={WORK[e.workStatus][1]}>{WORK[e.workStatus][0]}</StatusTag></div>
                <dl className="kv">
                  <dt>Mã nhân viên</dt><dd>{e.employeeCode}</dd>
                  <dt>Phòng ban</dt><dd>{e.departmentName}</dd>
                  <dt>Điện thoại</dt><dd>{e.phone ?? '—'}</dd>
                  <dt>Email</dt><dd>{e.email ?? '—'}</dd>
                  <dt>Ngày vào làm</dt><dd>{day(e.hireDate)}</dd>
                  {e.endDate && <><dt>Ngày nghỉ việc</dt><dd>{day(e.endDate)}</dd></>}
                  <dt>Tài khoản</dt><dd>{e.username ?? '—'} {e.accountStatus && `· ${ACCOUNT[e.accountStatus]}`}{e.mustChangePassword && ' · chờ đổi mật khẩu'}</dd>
                  <dt>Vai trò</dt><dd>{e.roles.map((r) => roleLabel[r]).join(', ') || '—'}</dd>
                </dl>
                {isMe && <div className="notice notice-warn">Đây là tài khoản của bạn: không tự khóa, tự cho nghỉ hay tự bỏ vai trò quản lý được.</div>}
                {e.workStatus !== 'LEFT' && (
                  <div className="toolbar">
                    <button className="btn btn-sm" onClick={() => openEdit(e)}>Sửa hồ sơ</button>
                    {e.accountId && <button className="btn btn-sm" onClick={() => openDialog('roles')}>Vai trò</button>}
                    {e.accountId && <button className="btn btn-sm" onClick={() => openDialog('reset')}>Đặt mật khẩu tạm</button>}
                    {e.accountStatus === 'ACTIVE' && !isMe && <button className="btn btn-sm" disabled={acct.isPending} onClick={() => acct.mutate('lock')}>Khóa tài khoản</button>}
                    {e.accountStatus === 'LOCKED' && <button className="btn btn-sm" disabled={acct.isPending} onClick={() => acct.mutate('unlock')}>Mở khóa</button>}
                    {!isMe && <button className="btn btn-sm btn-danger" onClick={() => openDialog('leave')}>Nghỉ việc</button>}
                  </div>
                )}
                <ErrorNotice error={dialog ? null : acct.error} />
              </>
            )}
          </aside>
        </div>
      )}

      <FormModal open={edit != null} wide title={edit === 'new' ? 'Thêm nhân viên' : 'Sửa hồ sơ'} submitLabel="Lưu nhân viên"
                 busy={save.isPending} error={save.error} onSubmit={() => save.mutate(undefined)} onClose={() => setEdit(null)}>
        <div className="form-grid">
          {edit === 'new' && <Field label="Mã nhân viên"><input className="input" required maxLength={20} value={form.employeeCode} onChange={(ev) => setForm({ ...form, employeeCode: ev.target.value })} /></Field>}
          <Field label="Họ tên"><input className="input" required maxLength={100} value={form.fullName} onChange={(ev) => setForm({ ...form, fullName: ev.target.value })} /></Field>
          <Field label="Phòng ban">
            <select className="input" required value={form.departmentId} onChange={(ev) => setForm({ ...form, departmentId: ev.target.value ? Number(ev.target.value) : '' })}>
              <option value="">Chọn…</option>{depts.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </Field>
          <Field label="Giới tính">
            <select className="input" value={form.gender} onChange={(ev) => setForm({ ...form, gender: ev.target.value })}>
              <option value="">—</option><option value="Nam">Nam</option><option value="Nữ">Nữ</option><option value="Khác">Khác</option>
            </select>
          </Field>
          <Field label="Ngày sinh"><input className="input" type="date" value={form.dateOfBirth} onChange={(ev) => setForm({ ...form, dateOfBirth: ev.target.value })} /></Field>
          <Field label="Điện thoại"><input className="input" type="tel" maxLength={15} value={form.phone} onChange={(ev) => setForm({ ...form, phone: ev.target.value })} /></Field>
          <Field label="Email"><input className="input" type="email" maxLength={100} value={form.email} onChange={(ev) => setForm({ ...form, email: ev.target.value })} /></Field>
          <Field label="Ngày vào làm"><input className="input" type="date" value={form.hireDate} onChange={(ev) => setForm({ ...form, hireDate: ev.target.value })} /></Field>
          {edit !== 'new' && (
            <Field label="Tình trạng">
              <select className="input" value={form.workStatus} onChange={(ev) => setForm({ ...form, workStatus: ev.target.value })}>
                <option value="ACTIVE">Đang làm</option><option value="INACTIVE">Tạm nghỉ</option>
              </select>
            </Field>
          )}
          <Field label="Địa chỉ" span><input className="input" maxLength={255} value={form.address} onChange={(ev) => setForm({ ...form, address: ev.target.value })} /></Field>
          {edit === 'new' && (
            <>
              <Field label="Tên đăng nhập" hint="Chữ, số, dấu chấm, gạch dưới."><input className="input" required minLength={3} maxLength={50} pattern="[A-Za-z0-9._\-]+" value={form.username} onChange={(ev) => setForm({ ...form, username: ev.target.value })} /></Field>
              <Field label="Mật khẩu tạm" hint="Ít nhất 8 ký tự; nhân viên đổi ở lần đăng nhập đầu."><input className="input" required minLength={8} maxLength={72} value={form.initialPassword} onChange={(ev) => setForm({ ...form, initialPassword: ev.target.value })} /></Field>
              <div className="field span-2">
                <span className="field-label">Vai trò</span>
                <div className="toolbar">
                  {roleList.data?.map((r) => (
                    <label key={r.id} className="check"><input type="checkbox" checked={form.roles.includes(r.roleCode)} onChange={() => setForm({ ...form, roles: toggleRole(form.roles, r.roleCode) })} />{r.roleName}</label>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>
      </FormModal>

      <FormModal open={dialog === 'roles'} title={`Vai trò của ${e?.fullName ?? ''}`} submitLabel="Lưu vai trò" busy={acct.isPending} error={acct.error}
                 onSubmit={() => acct.mutate('roles')} onClose={() => setDialog(null)} description="Có hiệu lực ngay, kể cả với phiên đang đăng nhập.">
        <div className="stack">
          {roleList.data?.map((r) => (
            <label key={r.id} className="check"><input type="checkbox" checked={roles.includes(r.roleCode)} onChange={() => setRoles(toggleRole(roles, r.roleCode))} />{r.roleName}</label>
          ))}
        </div>
      </FormModal>
      <FormModal open={dialog === 'reset'} title={`Đặt mật khẩu tạm cho ${e?.username ?? ''}`} submitLabel="Đặt mật khẩu" busy={acct.isPending} error={acct.error}
                 onSubmit={() => acct.mutate('reset')} onClose={() => setDialog(null)} description="Nhân viên phải đổi mật khẩu này ở lần đăng nhập tiếp theo.">
        <Field label="Mật khẩu tạm"><input className="input" required minLength={8} maxLength={72} value={password} onChange={(ev) => setPassword(ev.target.value)} autoFocus /></Field>
      </FormModal>
      <FormModal open={dialog === 'leave'} danger title={`${e?.fullName ?? ''} nghỉ việc?`} submitLabel="Ghi nhận nghỉ việc" busy={acct.isPending} error={acct.error}
                 onSubmit={() => acct.mutate('leave')} onClose={() => setDialog(null)} description="Tài khoản không đăng nhập được nữa; phiên đang mở bị chặn ngay. Không hoàn tác.">
        <Field label="Ngày nghỉ việc"><input className="input" type="date" required value={endDate} onChange={(ev) => setEndDate(ev.target.value)} /></Field>
      </FormModal>
      <FormModal open={deptEdit != null} title={deptEdit === 'new' ? 'Thêm phòng ban' : 'Đổi tên phòng ban'} submitLabel="Lưu" busy={saveDept.isPending} error={saveDept.error}
                 onSubmit={() => saveDept.mutate(undefined)} onClose={() => setDeptEdit(null)}>
        <Field label="Tên phòng ban"><input className="input" required maxLength={50} value={deptName} onChange={(ev) => setDeptName(ev.target.value)} autoFocus /></Field>
      </FormModal>
    </main>
  );
}

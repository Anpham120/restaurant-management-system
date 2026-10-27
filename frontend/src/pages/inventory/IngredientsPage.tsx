import { useState } from 'react';

import { api } from '../../api/client';
import type { Ingredient, KitchenType } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, StatusTag, Tabs, num, useAction } from '../../components/ui';
import { amount } from '../../lib/format';
import { useIngredients, useKitchenTypes } from './shared';

/** Nguyên liệu thô (đơn vị gốc, mức tối thiểu) và loại nguyên liệu bếp dùng trong công thức. */
export function IngredientsPage() {
  const isManager = useAuth().hasRole('MANAGER');
  const [tab, setTab] = useState<'raw' | 'kitchen'>('raw');
  const ings = useIngredients(false);
  const types = useKitchenTypes(false);

  const [ingEdit, setIngEdit] = useState<Ingredient | 'new' | null>(null);
  const [ingForm, setIngForm] = useState({ name: '', baseUnit: 'g', minQuantity: '0', expiryTracked: true, active: true });
  const [typeEdit, setTypeEdit] = useState<KitchenType | 'new' | null>(null);
  const [typeForm, setTypeForm] = useState({ rawIngredientId: '' as number | '', name: '', handlingType: 'DIRECT', shelfLifeHours: '', minimumQuantity: '0', active: true });

  const saveIng = useAction(
    () => {
      const body = { name: ingForm.name.trim(), baseUnit: ingForm.baseUnit.trim(), minQuantity: num(ingForm.minQuantity) ?? 0, expiryTracked: ingForm.expiryTracked, active: ingForm.active };
      return ingEdit === 'new' ? api.post('/api/ingredients', body) : api.put(`/api/ingredients/${(ingEdit as Ingredient).id}`, body);
    },
    { success: 'Đã lưu nguyên liệu', invalidate: [['ingredients'], ['kitchen-types']], onSuccess: () => setIngEdit(null) },
  );
  const saveType = useAction(
    () => {
      const body = { rawIngredientId: typeForm.rawIngredientId, name: typeForm.name.trim(), handlingType: typeForm.handlingType,
        shelfLifeHours: num(typeForm.shelfLifeHours), minimumQuantity: num(typeForm.minimumQuantity) ?? 0, active: typeForm.active };
      return typeEdit === 'new' ? api.post('/api/kitchen-ingredient-types', body) : api.put(`/api/kitchen-ingredient-types/${(typeEdit as KitchenType).id}`, body);
    },
    { success: 'Đã lưu loại nguyên liệu bếp', invalidate: [['kitchen-types']], onSuccess: () => setTypeEdit(null) },
  );

  function openIng(i: Ingredient | 'new') {
    setIngForm(i === 'new' ? { name: '', baseUnit: 'g', minQuantity: '0', expiryTracked: true, active: true }
      : { name: i.name, baseUnit: i.baseUnit, minQuantity: String(i.minQuantity), expiryTracked: i.expiryTracked, active: i.active });
    saveIng.reset();
    setIngEdit(i);
  }
  function openType(t: KitchenType | 'new') {
    setTypeForm(t === 'new' ? { rawIngredientId: '', name: '', handlingType: 'DIRECT', shelfLifeHours: '', minimumQuantity: '0', active: true }
      : { rawIngredientId: t.rawIngredientId, name: t.name, handlingType: t.handlingType, shelfLifeHours: t.shelfLifeHours == null ? '' : String(t.shelfLifeHours), minimumQuantity: String(t.minimumQuantity), active: t.active });
    saveType.reset();
    setTypeEdit(t);
  }

  return (
    <main className="main">
      <PageHead
        title="Nguyên liệu"
        subtitle="Nguyên liệu thô nhập vào kho; loại nguyên liệu bếp là thứ công thức dùng: dùng thẳng (cắt từ lô xuất) hoặc qua sơ chế."
        actions={isManager && (tab === 'raw'
          ? <button className="btn btn-primary" onClick={() => openIng('new')}>Thêm nguyên liệu</button>
          : <button className="btn btn-primary" onClick={() => openType('new')}>Thêm loại bếp</button>)}
      />
      <Tabs value={tab} onChange={setTab} options={[['raw', 'Nguyên liệu thô'], ['kitchen', 'Loại nguyên liệu bếp']]} />
      <ErrorNotice error={ings.error ?? types.error} />
      {tab === 'raw' ? (
        <DataTable rows={ings.data} rowKey={(i) => i.id} empty="Chưa có nguyên liệu."
          columns={[
            { header: 'Nguyên liệu', render: (i) => <b>{i.name}</b> },
            { header: 'Đơn vị gốc', render: (i) => i.baseUnit },
            { header: 'Mức tối thiểu', render: (i) => amount(i.minQuantity, i.baseUnit), align: 'right' },
            { header: 'Theo dõi hạn', render: (i) => (i.expiryTracked ? 'Có' : 'Không') },
            { header: 'Trạng thái', render: (i) => (i.active ? 'Đang dùng' : <StatusTag tone="stop">Ngừng</StatusTag>) },
            { header: '', render: (i) => isManager && <button className="btn btn-sm" onClick={() => openIng(i)}>Sửa</button> },
          ]} />
      ) : (
        <DataTable rows={types.data} rowKey={(t) => t.id} empty="Chưa có loại nguyên liệu bếp."
          columns={[
            { header: 'Loại bếp', render: (t) => <b>{t.name}</b> },
            { header: 'Từ nguyên liệu', render: (t) => t.rawIngredientName },
            { header: 'Cách dùng', render: (t) => (t.handlingType === 'DIRECT' ? 'Dùng thẳng' : 'Sau sơ chế') },
            { header: 'Hạn dùng tại bếp', render: (t) => (t.shelfLifeHours ? `${t.shelfLifeHours} giờ` : '—'), align: 'right' },
            { header: 'Tối thiểu', render: (t) => amount(t.minimumQuantity, t.baseUnit), align: 'right' },
            { header: 'Trạng thái', render: (t) => (t.active ? 'Đang dùng' : <StatusTag tone="stop">Ngừng</StatusTag>) },
            { header: '', render: (t) => isManager && <button className="btn btn-sm" onClick={() => openType(t)}>Sửa</button> },
          ]} />
      )}

      <FormModal open={ingEdit != null} title={ingEdit === 'new' ? 'Thêm nguyên liệu' : 'Sửa nguyên liệu'} submitLabel="Lưu nguyên liệu"
                 busy={saveIng.isPending} error={saveIng.error} onSubmit={() => saveIng.mutate(undefined)} onClose={() => setIngEdit(null)}>
        <div className="form-grid">
          <Field label="Tên nguyên liệu" span><input className="input" required maxLength={100} value={ingForm.name} onChange={(e) => setIngForm({ ...ingForm, name: e.target.value })} autoFocus /></Field>
          <Field label="Đơn vị gốc" hint="Đơn vị nhỏ nhất dùng trong công thức: g, ml, quả…"><input className="input" required maxLength={20} value={ingForm.baseUnit} onChange={(e) => setIngForm({ ...ingForm, baseUnit: e.target.value })} /></Field>
          <Field label="Mức tồn tối thiểu"><input className="input" type="number" min={0} step="any" value={ingForm.minQuantity} onChange={(e) => setIngForm({ ...ingForm, minQuantity: e.target.value })} /></Field>
          <label className="check"><input type="checkbox" checked={ingForm.expiryTracked} onChange={(e) => setIngForm({ ...ingForm, expiryTracked: e.target.checked })} />Bắt buộc ghi hạn dùng khi nhập</label>
          <label className="check"><input type="checkbox" checked={ingForm.active} onChange={(e) => setIngForm({ ...ingForm, active: e.target.checked })} />Đang dùng</label>
        </div>
      </FormModal>

      <FormModal open={typeEdit != null} title={typeEdit === 'new' ? 'Thêm loại nguyên liệu bếp' : 'Sửa loại nguyên liệu bếp'} submitLabel="Lưu"
                 busy={saveType.isPending} error={saveType.error} onSubmit={() => saveType.mutate(undefined)} onClose={() => setTypeEdit(null)}
                 description="Mỗi nguyên liệu có tối đa một loại dùng thẳng. Sau khi tạo không đổi được nguyên liệu gốc và cách dùng.">
        <div className="form-grid">
          <Field label="Nguyên liệu gốc">
            <select className="input" required disabled={typeEdit !== 'new'} value={typeForm.rawIngredientId}
                    onChange={(e) => setTypeForm({ ...typeForm, rawIngredientId: e.target.value ? Number(e.target.value) : '' })}>
              <option value="">Chọn…</option>
              {ings.data?.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
            </select>
          </Field>
          <Field label="Cách dùng">
            <select className="input" disabled={typeEdit !== 'new'} value={typeForm.handlingType} onChange={(e) => setTypeForm({ ...typeForm, handlingType: e.target.value })}>
              <option value="DIRECT">Dùng thẳng</option>
              <option value="PREPARED">Sau sơ chế</option>
            </select>
          </Field>
          <Field label="Tên loại bếp" span><input className="input" required maxLength={100} value={typeForm.name} onChange={(e) => setTypeForm({ ...typeForm, name: e.target.value })} /></Field>
          <Field label="Hạn dùng tại bếp (giờ)"><input className="input" type="number" min={1} value={typeForm.shelfLifeHours} onChange={(e) => setTypeForm({ ...typeForm, shelfLifeHours: e.target.value })} /></Field>
          <Field label="Mức tối thiểu tại bếp"><input className="input" type="number" min={0} step="any" value={typeForm.minimumQuantity} onChange={(e) => setTypeForm({ ...typeForm, minimumQuantity: e.target.value })} /></Field>
          <label className="check span-2"><input type="checkbox" checked={typeForm.active} onChange={(e) => setTypeForm({ ...typeForm, active: e.target.checked })} />Đang dùng</label>
        </div>
      </FormModal>
    </main>
  );
}

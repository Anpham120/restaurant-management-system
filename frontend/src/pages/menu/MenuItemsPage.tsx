import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import { api } from '../../api/client';
import type { Category, KitchenType, MenuItem, Recipe } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { DataTable, Field, FormModal, PageHead, StatusTag, Tabs, num, useAction } from '../../components/ui';
import { amount, vnd } from '../../lib/format';

type Line = { typeId: number | ''; qty: string };

/** Danh mục, món (giá bán), công thức theo phiên bản. Đổi giá không ảnh hưởng món đã gọi. */
export function MenuItemsPage() {
  const [catFilter, setCatFilter] = useState<number | 'all'>('all');
  const [showInactive, setShowInactive] = useState(false);
  const [selected, setSelected] = useState<MenuItem | null>(null);
  const [catEdit, setCatEdit] = useState<Category | 'new' | null>(null);
  const [catForm, setCatForm] = useState({ name: '', sortOrder: '0', active: true });
  const [itemEdit, setItemEdit] = useState<MenuItem | 'new' | null>(null);
  const [itemForm, setItemForm] = useState({ categoryId: '' as number | '', name: '', price: '', imageUrl: '', active: true });
  const [recipeOpen, setRecipeOpen] = useState(false);
  const [lines, setLines] = useState<Line[]>([]);
  const [view, setView] = useState<'items' | 'categories'>('items');

  const cats = useQuery({ queryKey: ['menu-categories', 'all'], queryFn: () => api.get<Category[]>('/api/menu-categories?activeOnly=false') });
  const items = useQuery({
    queryKey: ['menu-items', 'all'],
    queryFn: () => api.get<MenuItem[]>('/api/menu-items?activeOnly=false'),
  });
  const types = useQuery({ queryKey: ['kitchen-types', 'active'], queryFn: () => api.get<KitchenType[]>('/api/kitchen-ingredient-types') });
  const recipes = useQuery({
    queryKey: ['recipes', selected?.id],
    queryFn: () => api.get<Recipe[]>(`/api/menu-items/${selected!.id}/recipes`),
    enabled: selected != null,
  });

  const saveCat = useAction(
    () => {
      const body = { name: catForm.name.trim(), sortOrder: num(catForm.sortOrder) ?? 0, active: catForm.active };
      return catEdit === 'new' ? api.post('/api/menu-categories', body) : api.put(`/api/menu-categories/${(catEdit as Category).id}`, body);
    },
    { success: 'Đã lưu danh mục', invalidate: [['menu-categories'], ['menu-items']], onSuccess: () => setCatEdit(null) },
  );
  const saveItem = useAction(
    () => {
      const body = { categoryId: itemForm.categoryId, name: itemForm.name.trim(), price: num(itemForm.price), imageUrl: itemForm.imageUrl.trim() || null, active: itemForm.active };
      return itemEdit === 'new' ? api.post<MenuItem>('/api/menu-items', body) : api.put<MenuItem>(`/api/menu-items/${(itemEdit as MenuItem).id}`, body);
    },
    { success: 'Đã lưu món', invalidate: [['menu-items']], onSuccess: (r) => { setItemEdit(null); setSelected(r); } },
  );
  const saveRecipe = useAction(
    () => api.post<Recipe>(`/api/menu-items/${selected!.id}/recipes`, {
      ingredients: lines.filter((l) => l.typeId !== '').map((l) => ({ kitchenIngredientTypeId: l.typeId, quantityPerServing: num(l.qty) })),
    }),
    {
      success: (r) => `Đã lưu công thức phiên bản ${r.version}`,
      invalidate: [['recipes'], ['menu-items']],
      onSuccess: () => setRecipeOpen(false),
    },
  );

  const allItems = items.data ?? [];
  const shown = allItems.filter((i) => (showInactive || i.active) && (catFilter === 'all' || i.categoryId === catFilter));
  const current = selected ? allItems.find((i) => i.id === selected.id) ?? selected : null;

  function openCat(c: Category | 'new') {
    setCatForm(c === 'new' ? { name: '', sortOrder: '0', active: true } : { name: c.name, sortOrder: String(c.sortOrder), active: c.active });
    saveCat.reset();
    setCatEdit(c);
  }
  function openItem(i: MenuItem | 'new') {
    setItemForm(i === 'new'
      ? { categoryId: catFilter === 'all' ? '' : catFilter, name: '', price: '', imageUrl: '', active: true }
      : { categoryId: i.categoryId, name: i.name, price: String(i.price), imageUrl: i.imageUrl ?? '', active: i.active });
    saveItem.reset();
    setItemEdit(i);
  }
  function openRecipe() {
    const cur = recipes.data?.find((r) => r.current);
    setLines(cur ? cur.ingredients.map((g) => ({ typeId: g.kitchenIngredientTypeId, qty: String(g.quantityPerServing) })) : [{ typeId: '', qty: '' }]);
    saveRecipe.reset();
    setRecipeOpen(true);
  }

  return (
    <main className="main">
      <PageHead
        title="Món và công thức"
        actions={
          view === 'items'
            ? <button className="btn btn-primary" onClick={() => openItem('new')}>Thêm món</button>
            : <button className="btn btn-primary" onClick={() => openCat('new')}>Thêm danh mục</button>
        }
      />
      <Tabs value={view} onChange={setView} options={[['items', 'Món'], ['categories', 'Danh mục']]} />
      <ErrorNotice error={cats.error ?? items.error} />

      {view === 'categories' ? (
        <DataTable
          rows={cats.data}
          rowKey={(c) => c.id}
          empty="Chưa có danh mục."
          columns={[
            { header: 'Danh mục', render: (c) => <b>{c.name}</b> },
            { header: 'Thứ tự', render: (c) => c.sortOrder, align: 'right' },
            { header: 'Số món', render: (c) => allItems.filter((i) => i.categoryId === c.id).length, align: 'right' },
            { header: 'Trạng thái', render: (c) => (c.active ? 'Đang dùng' : <StatusTag tone="stop">Ẩn</StatusTag>) },
            { header: '', render: (c) => <button className="btn btn-sm" onClick={() => openCat(c)}>Sửa</button> },
          ]}
        />
      ) : (
        <div className="split">
          <div>
            <div className="filters">
              <Field label="Danh mục">
                <select className="input" value={catFilter} onChange={(e) => setCatFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}>
                  <option value="all">Tất cả</option>
                  {cats.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </Field>
              <label className="check">
                <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
                Hiện món ngừng bán
              </label>
            </div>
            <DataTable
              rows={items.data ? shown : undefined}
              rowKey={(i) => i.id}
              selectedKey={current?.id}
              onRowClick={setSelected}
              empty="Không có món nào."
              columns={[
                { header: 'Món', render: (i) => <><b>{i.name}</b><div className="muted">{i.categoryName}</div></> },
                { header: 'Giá', render: (i) => vnd(i.price), align: 'right' },
                { header: 'Công thức', render: (i) => (i.currentRecipeVersion == null ? <StatusTag tone="wait">Chưa có</StatusTag> : `Phiên bản ${i.currentRecipeVersion}`) },
                { header: 'Bán', render: (i) => (i.active ? 'Đang bán' : <StatusTag tone="stop">Ngừng</StatusTag>) },
              ]}
            />
          </div>

          <aside className="panel stack">
            {!current ? (
              <p className="muted pick-hint">Chọn một món để xem và sửa công thức.</p>
            ) : (
              <>
                <div className="row-between">
                  <h2>{current.name}</h2>
                  <button className="btn btn-sm" onClick={() => openItem(current)}>Sửa món</button>
                </div>
                <dl className="kv">
                  <dt>Danh mục</dt><dd>{current.categoryName}</dd>
                  <dt>Giá bán</dt><dd className="num">{vnd(current.price)}</dd>
                  <dt>Trạng thái</dt><dd>{current.active ? 'Đang bán' : 'Ngừng bán'}</dd>
                </dl>
                <div className="row-between">
                  <h2>Công thức</h2>
                  <button className="btn btn-sm btn-primary" onClick={openRecipe} disabled={!recipes.isSuccess}>
                    {recipes.data?.length ? 'Tạo phiên bản mới' : 'Tạo công thức'}
                  </button>
                </div>
                <ErrorNotice error={recipes.error} />
                {recipes.data?.length === 0 && <p className="muted">Món chưa có công thức nên chưa đưa vào thực đơn ngày được.</p>}
                {recipes.data?.map((r) => (
                  <div key={r.id} className="stack" style={{ gap: '0.35rem' }}>
                    <div className="row-between">
                      <b>Phiên bản {r.version}</b>
                      {r.current ? <StatusTag tone="go">Hiện hành</StatusTag> : <StatusTag>Cũ</StatusTag>}
                    </div>
                    {r.ingredients.length === 0 && <span className="muted">Không dùng nguyên liệu kho.</span>}
                    {r.ingredients.map((g) => (
                      <div key={g.kitchenIngredientTypeId} className="row-between" style={{ fontSize: 'var(--step--1)' }}>
                        <span>{g.typeName}</span>
                        <span className="num">{amount(g.quantityPerServing, g.baseUnit)} / suất</span>
                      </div>
                    ))}
                  </div>
                ))}
              </>
            )}
          </aside>
        </div>
      )}

      <FormModal open={catEdit != null} title={catEdit === 'new' ? 'Thêm danh mục' : 'Sửa danh mục'} submitLabel="Lưu danh mục"
                 busy={saveCat.isPending} error={saveCat.error} onSubmit={() => saveCat.mutate(undefined)} onClose={() => setCatEdit(null)}>
        <div className="form-grid">
          <Field label="Tên danh mục"><input className="input" required maxLength={100} value={catForm.name} onChange={(e) => setCatForm({ ...catForm, name: e.target.value })} autoFocus /></Field>
          <Field label="Thứ tự hiển thị"><input className="input" type="number" value={catForm.sortOrder} onChange={(e) => setCatForm({ ...catForm, sortOrder: e.target.value })} /></Field>
          <label className="check span-2"><input type="checkbox" checked={catForm.active} onChange={(e) => setCatForm({ ...catForm, active: e.target.checked })} />Đang dùng</label>
        </div>
      </FormModal>

      <FormModal open={itemEdit != null} title={itemEdit === 'new' ? 'Thêm món' : 'Sửa món'} submitLabel="Lưu món"
                 busy={saveItem.isPending} error={saveItem.error} onSubmit={() => saveItem.mutate(undefined)} onClose={() => setItemEdit(null)}
                 description="Đổi giá chỉ áp dụng cho món gọi sau đó.">
        <div className="form-grid">
          <Field label="Tên món" span><input className="input" required maxLength={100} value={itemForm.name} onChange={(e) => setItemForm({ ...itemForm, name: e.target.value })} autoFocus /></Field>
          <Field label="Danh mục">
            <select className="input" required value={itemForm.categoryId} onChange={(e) => setItemForm({ ...itemForm, categoryId: e.target.value ? Number(e.target.value) : '' })}>
              <option value="">Chọn…</option>
              {cats.data?.filter((c) => c.active).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Giá bán (đồng)"><input className="input" type="number" min={0} step={1000} required value={itemForm.price} onChange={(e) => setItemForm({ ...itemForm, price: e.target.value })} /></Field>
          <Field label="Link ảnh (tùy chọn)" span><input className="input" type="url" value={itemForm.imageUrl} onChange={(e) => setItemForm({ ...itemForm, imageUrl: e.target.value })} /></Field>
          <label className="check span-2"><input type="checkbox" checked={itemForm.active} onChange={(e) => setItemForm({ ...itemForm, active: e.target.checked })} />Đang bán</label>
        </div>
      </FormModal>

      <FormModal open={recipeOpen} wide title={`Công thức mới: ${current?.name ?? ''}`} submitLabel="Lưu thành phiên bản mới"
                 busy={saveRecipe.isPending} error={saveRecipe.error} onSubmit={() => saveRecipe.mutate(undefined)} onClose={() => setRecipeOpen(false)}
                 description="Phiên bản cũ được giữ nguyên cho các thực đơn đã dùng; thực đơn ngày thêm món sau đó dùng phiên bản mới.">
        <div className="lines-editor">
          {lines.map((l, idx) => {
            const t = types.data?.find((x) => x.id === l.typeId);
            return (
              <div className="le-row" key={idx} style={{ gridTemplateColumns: 'minmax(0,2fr) minmax(0,1fr) auto' }}>
                <Field label="Nguyên liệu bếp">
                  <select className="input" required value={l.typeId}
                          onChange={(e) => setLines(lines.map((x, i) => (i === idx ? { ...x, typeId: e.target.value ? Number(e.target.value) : '' } : x)))}>
                    <option value="">Chọn…</option>
                    {types.data?.map((t) => <option key={t.id} value={t.id}>{t.name} ({t.handlingType === 'DIRECT' ? 'dùng thẳng' : 'sơ chế'})</option>)}
                  </select>
                </Field>
                <Field label={`Định lượng / suất${t ? ` (${t.baseUnit})` : ''}`}>
                  <input className="input" type="number" min={0} step="any" required value={l.qty}
                         onChange={(e) => setLines(lines.map((x, i) => (i === idx ? { ...x, qty: e.target.value } : x)))} />
                </Field>
                <button type="button" className="btn btn-quiet" onClick={() => setLines(lines.filter((_, i) => i !== idx))}>Bỏ</button>
              </div>
            );
          })}
          <button type="button" className="btn" onClick={() => setLines([...lines, { typeId: '', qty: '' }])}>Thêm nguyên liệu</button>
        </div>
      </FormModal>
    </main>
  );
}

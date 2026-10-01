// Database-first check: the ERD in docs-core/07-erd.md must match what the Flyway migrations create.
// Reads every Mermaid erDiagram block of the ERD and every V<n>__*.sql migration in version order
// (CREATE TABLE and ALTER TABLE ... ADD COLUMN), then compares tables, columns, types and the keys of single
// columns: PK (primary key), FK (REFERENCES) and UK (UNIQUE). A UNIQUE over several columns and a partial unique
// index cannot be drawn on one column, so the ERD lists them in section 7.2 instead.
// Usage: node scripts/check-erd.mjs [docs-core/07-erd.md] [backend/src/main/resources/db/migration]
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const [erdPath = 'docs-core/07-erd.md', migrationDir = 'backend/src/main/resources/db/migration'] = process.argv.slice(2)

const normalize = (type) => {
  const t = type.toLowerCase()
  if (t.startsWith('varchar')) return 'varchar'
  if (t.startsWith('numeric')) return 'numeric'
  if (t === 'integer' || t === 'int') return 'int'
  return t
}

const KEYS = ['PK', 'FK', 'UK']
/** The keys a column definition declares, ignoring its comment. */
const keysOf = (definition) => {
  const text = definition.replace(/--.*$/m, '')
  return new Set([
    ...(/PRIMARY KEY/.test(text) ? ['PK'] : []),
    ...(/\bREFERENCES\b/.test(text) ? ['FK'] : []),
    ...(/\bUNIQUE\b/.test(text) ? ['UK'] : []),
  ])
}
const listed = (keys) => [...keys].sort().join(', ') || 'no key'

// An entity may appear in several diagrams (the HR diagram repeats EMPLOYEE); its columns are merged.
const erd = {}
for (const [, block] of readFileSync(erdPath, 'utf8').matchAll(/```mermaid\s+erDiagram([\s\S]*?)```/g)) {
  for (const [, entity, body] of block.matchAll(/^\s*([A-Z_]+)\s*\{([\s\S]*?)\}/gm)) {
    const columns = (erd[entity.toLowerCase()] ??= {})
    for (const line of body.split('\n').map((l) => l.trim()).filter(Boolean)) {
      const [type, name, ...rest] = line.replace(/"[^"]*"/g, '').split(/[\s,]+/)
      columns[name] = { type: normalize(type), keys: new Set(rest.filter((k) => KEYS.includes(k))) }
    }
  }
}

const sql = {}
const version = (file) => Number(file.match(/^V(\d+)__/)[1])
const files = readdirSync(migrationDir)
  .filter((f) => /^V\d+__.*\.sql$/.test(f))
  .sort((a, b) => version(a) - version(b))
for (const file of files) {
  const source = readFileSync(join(migrationDir, file), 'utf8')
  for (const [, table, body] of source.matchAll(/CREATE TABLE (\w+) \(([\s\S]*?)\n\);/g)) {
    const columns = (sql[table] = {})
    for (const line of body.split('\n').map((l) => l.trim())) {
      if (!line || line.startsWith('--')) continue
      const constraint = line.match(/^(?:CONSTRAINT \w+ )?(PRIMARY KEY|FOREIGN KEY|UNIQUE|CHECK)\s*\(([^)]*)\)/)
      if (constraint) {
        const names = constraint[2].split(',').map((c) => c.trim())
        const key = { 'PRIMARY KEY': 'PK', 'FOREIGN KEY': 'FK', UNIQUE: 'UK' }[constraint[1]]
        // A UNIQUE over several columns is not a key of any one of them.
        if (key && (key !== 'UK' || names.length === 1)) names.forEach((c) => columns[c]?.keys.add(key))
        continue
      }
      const [name, type] = line.split(/\s+/)
      columns[name] = { type: normalize(type.replace(/[(,].*$/, '')), keys: keysOf(line) }
    }
  }
  for (const [, table, body] of source.matchAll(/ALTER TABLE (\w+)\s+([\s\S]*?);/g)) {
    for (const definition of body.split(/ADD COLUMN/).slice(1)) {
      const [name, type] = definition.trim().split(/\s+/)
      ;(sql[table] ??= {})[name] = { type: normalize(type.replace(/[(,].*$/, '')), keys: keysOf(definition) }
    }
  }
}

let problems = 0
const report = (message) => {
  console.log(message)
  problems++
}
for (const table of new Set([...Object.keys(erd), ...Object.keys(sql)])) {
  if (!erd[table] || !sql[table]) {
    report(`TABLE ${table}: only in ${erd[table] ? 'the ERD' : 'the migrations'}`)
    continue
  }
  for (const column of new Set([...Object.keys(erd[table]), ...Object.keys(sql[table])])) {
    const inErd = erd[table][column]
    const inSql = sql[table][column]
    if (!inErd) report(`${table}.${column}: missing in the ERD`)
    else if (!inSql) report(`${table}.${column}: missing in the migrations`)
    else {
      if (inErd.type !== inSql.type) report(`${table}.${column}: the ERD says ${inErd.type}, the migrations say ${inSql.type}`)
      if (listed(inErd.keys) !== listed(inSql.keys)) {
        report(`${table}.${column}: the ERD marks ${listed(inErd.keys)}, the migrations make ${listed(inSql.keys)}`)
      }
    }
  }
}
const columnCount = Object.values(sql).reduce((n, columns) => n + Object.keys(columns).length, 0)
const keyCount = Object.values(sql).reduce((n, columns) => n + Object.values(columns).reduce((k, c) => k + c.keys.size, 0), 0)
console.log(`${files.length} migrations, ${Object.keys(sql).length} tables, ${columnCount} columns, ${keyCount} keys: ${problems} problem(s)`)
process.exit(problems ? 1 : 0)

// Database-first check: the ERD in docs-core/07-erd.md must match what the Flyway migrations create.
// Reads every Mermaid erDiagram block of the ERD and every V<n>__*.sql migration in version order
// (CREATE TABLE and ALTER TABLE ... ADD COLUMN), then compares tables, columns and types.
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

// An entity may appear in several diagrams (the HR diagram repeats EMPLOYEE); its columns are merged.
const erd = {}
for (const [, block] of readFileSync(erdPath, 'utf8').matchAll(/```mermaid\s+erDiagram([\s\S]*?)```/g)) {
  for (const [, entity, body] of block.matchAll(/^\s*([A-Z_]+)\s*\{([\s\S]*?)\}/gm)) {
    const columns = (erd[entity.toLowerCase()] ??= {})
    for (const line of body.split('\n').map((l) => l.trim()).filter(Boolean)) {
      const [type, name] = line.split(/\s+/)
      columns[name] = normalize(type)
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
      if (!line || line.startsWith('--') || line.startsWith('CONSTRAINT')) continue
      const [name, type] = line.split(/\s+/)
      columns[name] = normalize(type.replace(/[(,].*$/, ''))
    }
  }
  for (const [, table, body] of source.matchAll(/ALTER TABLE (\w+)\s+([\s\S]*?);/g)) {
    for (const [, name, type] of body.matchAll(/ADD COLUMN (\w+)\s+(\w+)/g)) {
      (sql[table] ??= {})[name] = normalize(type)
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
    if (!(column in erd[table])) report(`${table}.${column}: missing in the ERD`)
    else if (!(column in sql[table])) report(`${table}.${column}: missing in the migrations`)
    else if (erd[table][column] !== sql[table][column]) {
      report(`${table}.${column}: the ERD says ${erd[table][column]}, the migrations say ${sql[table][column]}`)
    }
  }
}
const columnCount = Object.values(sql).reduce((n, columns) => n + Object.keys(columns).length, 0)
console.log(`${files.length} migrations, ${Object.keys(sql).length} tables, ${columnCount} columns: ${problems} problem(s)`)
process.exit(problems ? 1 : 0)

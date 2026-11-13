package mydb;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Database {
    public List<Table> tables = new ArrayList<>();
    private final String FILE_NAME = "database.dat";

    public void executeQuery(String sql) {
        sql = sql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

        try {
            if (sql.toUpperCase().startsWith("CREATE TABLE")) {
                handleCreateTable(sql);
            } else if (sql.toUpperCase().startsWith("INSERT INTO")) {
                handleInsert(sql);
            } else if (sql.toUpperCase().startsWith("SELECT")) {
                handleSelect(sql);
            } else if (sql.toUpperCase().startsWith("UPDATE")) {
                handleUpdate(sql);
            } else if (sql.toUpperCase().startsWith("DELETE FROM")) {
                handleDelete(sql);
            } else if (sql.toUpperCase().startsWith("CREATE INDEX")) {
                handleCreateIndex(sql);
            } else {
                System.out.println("Ошибка: Неизвестная команда.");
            }
        } catch (Exception e) {
            System.out.println("Ошибка выполнения: " + e.getMessage());
        }
    }

    private void handleCreateTable(String sql) {
        Pattern p = Pattern.compile("CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) throw new IllegalArgumentException("Неверный синтаксис CREATE TABLE");

        String tableName = m.group(1);
        Table t = new Table(tableName);

        String[] fields = m.group(2).split(",");
        for (String field : fields) {
            String[] tokens = field.trim().split("\\s+");
            t.columnNames.add(tokens[0]);
            t.columnTypes.add(tokens[1]);
        }
        tables.add(t);
        saveToDisk();
        System.out.println("Таблица " + tableName + " создана.");
    }

    private void handleInsert(String sql) {
        Pattern p = Pattern.compile("INSERT\\s+INTO\\s+(\\w+)\\s*(?:\\(([^)]+)\\))?\\s*VALUES\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) throw new IllegalArgumentException("Неверный синтаксис INSERT");

        Table t = findTable(m.group(1));
        if (t == null) throw new IllegalArgumentException("Таблица не найдена");

        String colsStr = m.group(2);
        String valsStr = m.group(3);
        String[] rawValues = valsStr.split(",");

        List<Object> row = new ArrayList<>(Collections.nCopies(t.columnNames.size(), null));

        if (colsStr == null) {
            for (int i = 0; i < t.columnNames.size(); i++) {
                if (i < rawValues.length) {
                    row.set(i, t.castValue(rawValues[i], t.columnTypes.get(i)));
                }
            }
        } else {
            String[] cols = colsStr.split(",");
            for (int i = 0; i < cols.length; i++) {
                int colIdx = t.getColumnIndex(cols[i].trim());
                row.set(colIdx, t.castValue(rawValues[i], t.columnTypes.get(colIdx)));
            }
        }

        if (!t.isPrimaryKeyUnique(row.get(0))) {
            System.out.println("Ошибка: Нарушение уникальности первичного ключа (id=" + row.get(0) + ")");
            return;
        }

        t.rows.add(row);
        t.rebuildIndexes();
        saveToDisk();
        System.out.println("Запись успешно добавлена.");
    }

    private void handleSelect(String sql) {
        Pattern p = Pattern.compile("SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) throw new IllegalArgumentException("Неверный синтаксис SELECT");

        String selectCols = m.group(1).trim();
        Table t = findTable(m.group(2));
        if (t == null) throw new IllegalArgumentException("Таблица не найдена");
        String whereClause = m.group(3);

        List<Integer> matchedRows = new ArrayList<>();
        boolean usedIndex = false;

        long startTime = System.nanoTime();

        if (whereClause != null && whereClause.contains("=")) {
            String[] tokens = whereClause.split("=");
            String colName = tokens[0].trim();
            String valStr = tokens[1].trim();

            for (Index idx : t.indexes) {
                if (idx.columnName.equalsIgnoreCase(colName)) {
                    int colIdx = t.getColumnIndex(colName);
                    Object castedTarget = t.castValue(valStr, t.columnTypes.get(colIdx));
                    List<Integer> ids = idx.indexMap.get(castedTarget);
                    if (ids != null) matchedRows.addAll(ids);
                    usedIndex = true;
                    break;
                }
            }
        }

        if (!usedIndex) {
            for (int i = 0; i < t.rows.size(); i++) {
                if (evaluateWhere(t, i, whereClause)) {
                    matchedRows.add(i);
                }
            }
        }
        long endTime = System.nanoTime();

        List<String> outputCols = new ArrayList<>();
        if (selectCols.equals("*")) {
            outputCols.addAll(t.columnNames);
        } else {
            for (String s : selectCols.split(",")) outputCols.add(s.trim());
        }

        for (String col : outputCols) System.out.print(col + "\t\t");
        System.out.println("\n------------------------------------------------");
        for (int idx : matchedRows) {
            for (String col : outputCols) {
                int colIdx = t.getColumnIndex(col);
                System.out.print(t.rows.get(idx).get(colIdx) + "\t\t");
            }
            System.out.println();
        }
        System.out.printf("[Запрос выполнен за %d нс. Оптимизация индексом: %b]%n", (endTime - startTime), usedIndex);
    }

    private void handleUpdate(String sql) {
        Pattern p = Pattern.compile("UPDATE\\s+(\\w+)\\s+SET\\s+(\\w+)\\s*=\\s*(.+?)(?:\\s+WHERE\\s+(.+))?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) throw new IllegalArgumentException("Неверный синтаксис UPDATE");

        Table t = findTable(m.group(1));
        if (t == null) throw new IllegalArgumentException("Таблица не найдена");

        String targetCol = m.group(2);
        String newValStr = m.group(3);
        String whereClause = m.group(4);

        int colIdx = t.getColumnIndex(targetCol);
        Object newVal = t.castValue(newValStr, t.columnTypes.get(colIdx));

        int updatedCount = 0;
        for (int i = 0; i < t.rows.size(); i++) {
            if (evaluateWhere(t, i, whereClause)) {
                t.rows.get(i).set(colIdx, newVal);
                updatedCount++;
            }
        }
        if (updatedCount > 0) {
            t.rebuildIndexes();
            saveToDisk();
        }
        System.out.println(updatedCount + " строк обновлено.");
    }

    private void handleDelete(String sql) {
        Pattern p = Pattern.compile("DELETE\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) throw new IllegalArgumentException("Неверный синтаксис DELETE");

        Table t = findTable(m.group(1));
        if (t == null) throw new IllegalArgumentException("Таблица не найдена");
        String whereClause = m.group(2);

        int initialSize = t.rows.size();
        for (int i = t.rows.size() - 1; i >= 0; i--) {
            if (evaluateWhere(t, i, whereClause)) {
                t.rows.remove(i);
            }
        }
        int deletedCount = initialSize - t.rows.size();
        if (deletedCount > 0) {
            t.rebuildIndexes();
            saveToDisk();
        }
        System.out.println(deletedCount + " строк удалено.");
    }

    private void handleCreateIndex(String sql) {
        Pattern p = Pattern.compile("CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\((\\w+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) throw new IllegalArgumentException("Неверный синтаксис CREATE INDEX");

        String indexName = m.group(1);
        Table t = findTable(m.group(2));
        String colName = m.group(3);

        if (t == null || t.getColumnIndex(colName) == -1) throw new IllegalArgumentException("Таблица или колонка не найдена");

        Index newIdx = new Index(indexName, colName);
        t.indexes.add(newIdx);
        t.rebuildIndexes();
        saveToDisk();
        System.out.println("Индекс " + indexName + " успешно создан.");
    }

    private boolean evaluateWhere(Table t, int rowIndex, String whereClause) {
        if (whereClause == null || whereClause.trim().isEmpty()) return true;

        String op = "";
        if (whereClause.contains("=")) op = "=";
        else if (whereClause.contains(">")) op = ">";
        else if (whereClause.contains("<")) op = "<";
        else return false;

        String[] tokens = whereClause.split(op);
        int colIdx = t.getColumnIndex(tokens[0].trim());
        if (colIdx == -1) return false;

        Object cellVal = t.rows.get(rowIndex).get(colIdx);
        Object compareVal = t.castValue(tokens[1].trim(), t.columnTypes.get(colIdx));

        if (cellVal == null || compareVal == null) return false;

        if (op.equals("=")) {
            return cellVal.equals(compareVal);
        } else if (cellVal instanceof Comparable) {
            Comparable c1 = (Comparable) cellVal;
            Comparable c2 = (Comparable) compareVal;
            int res = c1.compareTo(c2);
            if (op.equals(">")) return res > 0;
            if (op.equals("<")) return res < 0;
        }
        return false;
    }

    public Table findTable(String name) {
        for (Table t : tables) {
            if (t.name.equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    public void saveToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(tables);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения: " + e.getMessage());
        }
    }
    @SuppressWarnings("unchecked")
    public void loadFromDisk() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            tables = (List) ois.readObject();
        } catch (Exception e) {
            System.out.println("База данных пуста или файл поврежден. Создана новая.");
        }
    }
}

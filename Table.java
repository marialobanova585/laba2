package mydb;

import java.io.Serializable;
import java.util.*;

public class Table implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public List<String> columnNames = new ArrayList<>();
    public List<String> columnTypes = new ArrayList<>();
    public List<List<Object>> rows = new ArrayList<>();
    public List<Index> indexes = new ArrayList<>();

    public Table(String name) {
        this.name = name;
    }

    public int getColumnIndex(String colName) {
        for (int i = 0; i < columnNames.size(); i++) {
            if (columnNames.get(i).equalsIgnoreCase(colName)) return i;
        }
        return -1;
    }

    public Object castValue(String val, String type) {
        if (val == null || val.equalsIgnoreCase("NULL")) return null;
        val = val.trim().replaceAll("^'|'$", "");

        switch (type.toUpperCase()) {
            case "INT":
                return Integer.parseInt(val);
            case "FLOAT":
                return Double.parseDouble(val);
            case "BOOLEAN":
            case "TRUE":
            case "FALSE":
                return Boolean.parseBoolean(val);
            case "STRING":
            case "DATE":
                return val;
            default:
                return val;
        }
    }

    public boolean isPrimaryKeyUnique(Object pkValue) {
        for (List<Object> row : rows) {
            if (row.size() > 0 && row.get(0).equals(pkValue)) {
                return false;
            }
        }
        return true;
    }

    public void rebuildIndexes() {
        for (Index idx : indexes) {
            idx.indexMap.clear();
            int colIdx = getColumnIndex(idx.columnName);
            for (int i = 0; i < rows.size(); i++) {
                idx.add(rows.get(i).get(colIdx), i);
            }
        }
    }
}


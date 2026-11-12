package mydb;

import java.io.Serializable;
import java.util.*;

public class Index implements Serializable {
    private static final long serialVersionUID = 1L;

    public String indexName;
    public String columnName;
    public Map<Object, List<Integer>> indexMap = new HashMap<>();

    public Index(String indexName, String columnName) {
        this.indexName = indexName;
        this.columnName = columnName;
    }

    public void add(Object key, int rowIndex) {
        if (!indexMap.containsKey(key)) {
            indexMap.put(key, new ArrayList<>());
        }
        indexMap.get(key).add(rowIndex);
    }
}

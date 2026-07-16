package io.coreplatform.billing.api.response;

import java.util.List;

public class PagedResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private int total;
    private boolean hasNext;

    public PagedResponse(List<T> items, int page, int size, int total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.hasNext = page * size < total;
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getTotal() { return total; }
    public boolean isHasNext() { return hasNext; }
}
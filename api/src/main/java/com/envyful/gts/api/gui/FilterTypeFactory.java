package com.envyful.gts.api.gui;

import java.util.ArrayList;
import java.util.List;

public class FilterTypeFactory {

    private static final List<FilterType> FILTER_TYPES = new ArrayList<>();

    public static void init() {

    }

    public static void register(FilterType filterType) {
        FILTER_TYPES.add(filterType);
    }

    public static FilterType getDefault() {
        return FILTER_TYPES.get(0);
    }

    public static FilterType getNext(FilterType filterType) {
        int index = FILTER_TYPES.indexOf(filterType);
        return FILTER_TYPES.get((index + 1) % FILTER_TYPES.size());
    }
}

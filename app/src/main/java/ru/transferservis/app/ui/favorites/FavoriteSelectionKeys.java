package ru.transferservis.app.ui.favorites;

/** Только локальная передача выбранного текста адреса в HomeFragment. */
public final class FavoriteSelectionKeys {

    public static final String ARG_TARGET = "favorite_target";
    public static final String TARGET_FROM = "from";
    public static final String TARGET_TO = "to";
    public static final String RESULT_FROM = "favorite_selected_from";
    public static final String RESULT_TO = "favorite_selected_to";

    private FavoriteSelectionKeys() {
    }
}

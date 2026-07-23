package fr.epistudio.epysia.editor.ui;

record AtlasCanvas(float originX, float originY, float width, float height) {

    float uvToScreenX(float u) {
        return originX + u * width;
    }

    float uvToScreenY(float v) {
        return originY + (1.0f - v) * height;
    }

    int columnAt(float screenX, int columns) {
        int column = (int) ((screenX - originX) / (width / columns));
        return Math.clamp(column, 0, columns - 1);
    }

    int rowAt(float screenY, int rows) {
        int row = (int) ((screenY - originY) / (height / rows));
        return Math.clamp(row, 0, rows - 1);
    }

    int cellIndexAt(float screenX, float screenY, int columns, int rows) {
        return rowAt(screenY, rows) * columns + columnAt(screenX, columns);
    }
}

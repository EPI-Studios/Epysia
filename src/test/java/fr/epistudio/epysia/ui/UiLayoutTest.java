package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLayoutTest {

    private static final float TOLERANCE = 1.0e-3f;
    private static final UiRect SCREEN = new UiRect(0.0f, 0.0f, 800.0f, 600.0f);

    @Test
    void aVerticalListStacksChildrenWithSpacing() {
        UiPanel container = container(new UiListLayout().setSpacing(10.0f));
        UiPanel first = child(container, 100.0f, 40.0f);
        UiPanel second = child(container, 100.0f, 40.0f);
        UiPanel third = child(container, 100.0f, 40.0f);

        container.layout(SCREEN);

        assertEquals(0.0f, first.computedRect().y(), TOLERANCE, "the first child starts at the top");
        assertEquals(50.0f, second.computedRect().y(), TOLERANCE, "height plus spacing");
        assertEquals(100.0f, third.computedRect().y(), TOLERANCE, "and again for the third");
    }

    @Test
    void paddingInsetsTheWholeRun() {
        UiPanel container = container(new UiListLayout().setSpacing(0.0f));
        ((UiListLayout) container.ownerOrNull().getComponentOrNull(UiLayout.class)).setPadding(12.0f);
        UiPanel first = child(container, 100.0f, 40.0f);

        container.layout(SCREEN);

        assertEquals(12.0f, first.computedRect().x(), TOLERANCE, "padding moves children in on x");
        assertEquals(12.0f, first.computedRect().y(), TOLERANCE, "and on y");
    }

    @Test
    void aHorizontalListRunsAcross() {
        UiPanel container = container(new UiListLayout()
                .setDirection(UiListDirection.HORIZONTAL).setSpacing(5.0f));
        UiPanel first = child(container, 60.0f, 30.0f);
        UiPanel second = child(container, 60.0f, 30.0f);

        container.layout(SCREEN);

        assertEquals(0.0f, first.computedRect().x(), TOLERANCE, "the first child starts at the left");
        assertEquals(65.0f, second.computedRect().x(), TOLERANCE, "width plus spacing");
        assertEquals(first.computedRect().y(), second.computedRect().y(), TOLERANCE,
                "a horizontal run keeps them on one line");
    }

    @Test
    void stretchFillsTheCrossAxis() {
        UiPanel container = container(new UiListLayout()
                .setCrossAlignment(UiListAlignment.STRETCH));
        UiPanel first = child(container, 60.0f, 30.0f);

        container.layout(SCREEN);

        assertEquals(container.computedRect().width(), first.computedRect().width(), TOLERANCE,
                "stretch makes the child span the container");
    }

    @Test
    void centreAlignmentCentresOnTheCrossAxis() {
        UiPanel container = container(new UiListLayout()
                .setCrossAlignment(UiListAlignment.CENTER));
        UiPanel first = child(container, 100.0f, 30.0f);

        container.layout(SCREEN);

        float expected = (container.computedRect().width() - 100.0f) * 0.5f;
        assertEquals(expected, first.computedRect().x(), TOLERANCE, "the child sits in the middle");
    }

    @Test
    void anInvisibleChildTakesNoSpace() {
        UiPanel container = container(new UiListLayout().setSpacing(10.0f));
        UiPanel first = child(container, 100.0f, 40.0f);
        UiPanel hidden = child(container, 100.0f, 40.0f);
        hidden.setVisible(false);
        UiPanel third = child(container, 100.0f, 40.0f);

        container.layout(SCREEN);

        assertEquals(0.0f, first.computedRect().y(), TOLERANCE, "the first child is unaffected");
        assertEquals(50.0f, third.computedRect().y(), TOLERANCE,
                "a hidden child must not leave a gap behind it");
    }

    @Test
    void aGridWrapsAtItsColumnCount() {
        UiPanel container = container(new UiGridLayout()
                .setCellSize(50.0f, 50.0f).setSpacing(10.0f).setColumns(2));
        UiPanel a = child(container, 10.0f, 10.0f);
        UiPanel b = child(container, 10.0f, 10.0f);
        UiPanel c = child(container, 10.0f, 10.0f);

        container.layout(SCREEN);

        assertEquals(0.0f, a.computedRect().x(), TOLERANCE, "first cell, first column");
        assertEquals(60.0f, b.computedRect().x(), TOLERANCE, "second cell, cell plus spacing");
        assertEquals(0.0f, c.computedRect().x(), TOLERANCE, "third cell wraps back to column zero");
        assertEquals(60.0f, c.computedRect().y(), TOLERANCE, "and drops to the next row");
    }

    @Test
    void aGridWithoutAColumnCountFitsTheWidth() {
        UiPanel container = container(new UiGridLayout()
                .setCellSize(100.0f, 50.0f).setSpacing(0.0f).setColumns(0));
        UiGridLayout grid = (UiGridLayout) container.ownerOrNull().getComponentOrNull(UiLayout.class);
        child(container, 10.0f, 10.0f);

        container.layout(SCREEN);

        assertTrue(grid.columns() == 0, "the authored value stays zero, meaning automatic");
    }

    @Test
    void aContainerWithoutALayoutLeavesChildrenAlone() {
        GameObject object = new GameObject("plain");
        object.addComponent(new Transform3D());
        UiPanel container = object.addComponent(new UiPanel());
        container.setSize(0.0f, 400.0f, 0.0f, 300.0f);
        UiPanel first = child(container, 100.0f, 40.0f);
        first.setPosition(0.0f, 33.0f, 0.0f, 77.0f);

        container.layout(SCREEN);

        assertEquals(33.0f, first.computedRect().x(), TOLERANCE,
                "without a layout the element keeps the position it was given");
        assertEquals(77.0f, first.computedRect().y(), TOLERANCE, "on both axes");
    }

    private static UiPanel container(UiLayout layout) {
        GameObject object = new GameObject("container");
        object.addComponent(new Transform3D());
        UiPanel panel = object.addComponent(new UiPanel());
        panel.setSize(0.0f, 400.0f, 0.0f, 300.0f);
        object.addComponent(layout);
        return panel;
    }

    private static UiPanel child(UiPanel parent, float width, float height) {
        GameObject object = new GameObject("child");
        object.addComponent(new Transform3D());
        UiPanel panel = object.addComponent(new UiPanel());
        panel.setSize(0.0f, width, 0.0f, height);
        object.setParent(parent.ownerOrNull());
        return panel;
    }
}

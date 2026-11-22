package local.tt;

import de.audi.atip.hmi.model.ChoiceListener;
import de.audi.atip.hmi.model.ChoiceModel;

/**
 * v63: the "iPhone turn card" drop-down's listener. The stock ComboBoxController calls
 * ChoiceModel.itemSelected(value, terminal), which calls us with (modelId, value, 0, terminal).
 * The stock model does not keep the choice itself: setValue() stores it and redraws.
 */
final class CardChoice implements ChoiceListener {
    private final ChoiceModel model;

    CardChoice(ChoiceModel model) {
        this.model = model;
    }

    public void itemSelected(int id, int value, int zero, int terminal) {
        RgdBus.note(new StringBuffer("settings page: card selected ").append(value)
            .append(" (model ").append(id).append(", terminal ").append(terminal).append(')').toString());
        try {
            if (value < 0 || value > 2) {
                return;
            }
            Card.setMode(value);
            model.setValue(value);
            RgdBus.note(new StringBuffer("settings page: card now ").append(Card.mode())
                .append(", model ").append(model.getValue()).toString());
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("settings page: card select failed ").append(String.valueOf(t)).toString());
        }
    }

    public void itemFocused(int id, int value, int zero, int terminal) {
    }

    public void keyPressed(int a, int b, int c) {
    }

    public void keyReleased(int a, int b, int c) {
    }

    public void keyTyped(int a, int b, int c) {
    }

    public void keyLongTyped(int a, int b, int c) {
    }
}

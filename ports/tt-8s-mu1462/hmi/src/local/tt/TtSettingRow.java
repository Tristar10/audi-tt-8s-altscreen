package local.tt;

import java.util.List;

import de.esolutions.hmi.widgets.audi.base.widgets.AbstractWidgetController;
import de.esolutions.hmi.widgets.audi.evo.widgets.ComboBoxController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuItemController;

/**
 * v63: a stock drop-down menu row with our own choice model. Only adds logging after the screen
 * is connected (which children the row created, whether its drop-down got our model) and hands
 * the model to the drop-down if the stock code did not.
 */
public class TtSettingRow extends MenuItemController {
    /** Our ChoiceModel (set by TtSettingsPage). */
    public Object ttModel;
    /** Where the row was added (for the log). */
    public String where = "";

    public TtSettingRow() {
        super();
    }

    protected void afterConnected() {
        super.afterConnected();
        try {
            StringBuffer b = new StringBuffer("settings page: row connected in ").append(where).append(", model ")
                .append(getModel() == ttModel ? "ours" : String.valueOf(getModel())).append(", children");
            List kids = getChildren();
            for (int i = 0; kids != null && i < kids.size(); i++) {
                Object c = kids.get(i);
                String n = c.getClass().getName();
                b.append(' ').append(n.substring(n.lastIndexOf('.') + 1));
                if (c instanceof ComboBoxController) {
                    ComboBoxController cb = (ComboBoxController) c;
                    Object m = cb.getModel();
                    if (m == ttModel) {
                        b.append("(model ours)");
                    } else {
                        b.append("(model ").append(String.valueOf(m)).append(" -> ours)");
                        cb.setModel(ttModel);
                    }
                }
            }
            RgdBus.note(b.toString());
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("settings page: row connect log failed ").append(String.valueOf(t)).toString());
        }
    }
}

package io.joshuasalcedo.schema.demo;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.FieldType;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.SchemaException;
import io.joshuasalcedo.schema.action.Action;
import io.joshuasalcedo.schema.action.Step;
import io.joshuasalcedo.schema.engine.Engine;
import io.joshuasalcedo.schema.record.Record;

/**
 * A sample desktop app on the platform. Nothing here knows about customers or orders: the model list, the tables,
 * the forms and the action buttons are all built at runtime from the definitions in {@link #seed}. Add a model or an
 * action there and it shows up with no screen code.
 * <p>
 * Run: {@code mvn -pl library/swaswa-schema-model test-compile exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=io.joshuasalcedo.schema.demo.PlatformDemo}
 */
public final class PlatformDemo {

    private final JTextArea log = new JTextArea();
    private final Engine engine;
    private final DefaultListModel<Model> modelList = new DefaultListModel<>();
    private final JList<Model> models = new JList<>(modelList);
    private final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JPanel actionButtons = new JPanel();
    private final JTextArea definition = new JTextArea();
    private List<Record> shown = List.of();

    private PlatformDemo() {
        engine = Engine.builder()
                .events(e -> SwingUtilities.invokeLater(() -> log("event  " + e.name()
                        + (e.action().isEmpty() ? "" : "  (action " + e.action() + ")")
                        + "  " + (e.after() != null ? e.after().key() + " v" + e.after().version()
                                                    : e.before().key()))))
                .handler("loyalty.bonus", ctx -> {
                    long points = (Long) ctx.values().getOrDefault("points", 0L);
                    ctx.values().put("points", points + 100);
                })
                .build();
        seed(engine);
    }

    /** The whole "application": three models, five actions and some data. */
    private static void seed(Engine engine) {
        engine.models().createModel(Model.valueObject("Address")
                .description("Where a customer lives")
                .field(Field.text("street").required())
                .field(Field.text("city").required())
                .field(Field.text("postcode").pattern("[0-9]{4}"))
                .build());
        engine.models().createModel(Model.entity("Customer")
                .description("Someone who buys from us")
                .field(Field.text("id").identity().required().pattern("c-[0-9]+"))
                .field(Field.text("name").required())
                .field(Field.text("email").required().pattern(".+@.+"))
                .field(Field.choice("status", "active", "closed").required())
                .field(Field.integer("points"))
                .field(Field.decimal("balance"))
                .field(Field.date("since"))
                .field(Field.text("closedReason"))
                .field(Field.model("address", "address"))
                .build());
        engine.models().createModel(Model.entity("Order")
                .field(Field.text("number").identity().required())
                .field(Field.model("customer", "customer").required())
                .field(Field.decimal("total").required())
                .field(Field.choice("state", "open", "paid", "shipped").required())
                .build());

        engine.defineAction(Action.named("CloseAccount")
                .description("Close an active customer's account")
                .on("customer")
                .input(Field.text("reason").required())
                .require("record.status == 'active'", "Only active accounts can be closed")
                .set("status", "'closed'")
                .set("closedReason", "input.reason")
                .emit("customer.closed")
                .build());
        engine.defineAction(Action.named("Reopen")
                .on("customer")
                .require("record.status == 'closed'", "The account is not closed")
                .set("status", "'active'")
                .set("closedReason", "null")
                .call("loyalty.bonus")
                .emit("customer.reopened")
                .build());
        engine.defineAction(Action.named("AddPoints")
                .on("customer")
                .input(Field.integer("points").required())
                .require("input.points > 0", "Points must be positive")
                .require("record.status == 'active'", "Closed accounts do not earn points")
                .set("points", "(has(record.points) ? record.points : 0) + input.points")
                .build());
        engine.defineAction(Action.named("Pay")
                .on("order")
                .require("record.state == 'open'", "Only open orders can be paid")
                .set("state", "'paid'")
                .emit("order.paid")
                .build());
        engine.defineAction(Action.named("Ship")
                .on("order")
                .require("record.state == 'paid'", "Pay the order first")
                .set("state", "'shipped'")
                .emit("order.shipped")
                .build());

        engine.create("customer", Map.of("id", "c-1", "name", "Alice", "email", "alice@example.com",
                                         "status", "active", "points", 120, "balance", "12.50", "since", "2025-03-01",
                                         "address", Map.of("street", "1 Rizal Ave", "city", "Manila")));
        engine.create("customer", Map.of("id", "c-2", "name", "Bob", "email", "bob@example.com",
                                         "status", "closed", "closedReason", "moved away"));
        engine.create("order", Map.of("number", "o-100", "customer", "c-1", "total", "49.90", "state", "open"));
    }

    private void show() {
        JFrame frame = new JFrame("Platform sample: everything below is generated from definitions");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // left: the entity models
        engine.models().listModels().stream().filter(Model::isEntity).forEach(modelList::addElement);
        models.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        models.setCellRenderer((list, model, index, selected, focus) -> {
            JLabel label = new JLabel(model.displayName() + "  (" + engine.list(model.id()).size() + ")");
            label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            label.setOpaque(selected);
            if (selected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        models.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refresh();
            }
        });
        JPanel left = new JPanel(new BorderLayout());
        left.add(header("Models"), BorderLayout.NORTH);
        left.add(new JScrollPane(models), BorderLayout.CENTER);

        // center: records + toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(button("New", this::createRecord));
        toolbar.add(button("Edit", this::editRecord));
        toolbar.add(button("Delete", this::deleteRecord));
        toolbar.addSeparator();
        toolbar.add(new JLabel("Actions: "));
        toolbar.add(actionButtons);
        toolbar.add(javax.swing.Box.createHorizontalGlue());
        toolbar.add(button("Light / dark", this::toggleTheme));
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel center = new JPanel(new BorderLayout());
        center.add(toolbar, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);

        // right: the definition of the selected model and its actions
        definition.setEditable(false);
        definition.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel right = new JPanel(new BorderLayout());
        right.add(header("Definition"), BorderLayout.NORTH);
        right.add(new JScrollPane(definition), BorderLayout.CENTER);

        // bottom: event log
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(header("Events and errors"), BorderLayout.NORTH);
        bottom.add(new JScrollPane(log), BorderLayout.CENTER);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, right);
        main.setResizeWeight(0.7);
        JSplitPane body = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, main);
        body.setDividerLocation(180);
        JSplitPane all = new JSplitPane(JSplitPane.VERTICAL_SPLIT, body, bottom);
        all.setResizeWeight(0.75);

        frame.add(all);
        frame.setSize(new Dimension(1280, 760));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        models.setSelectedIndex(0);
        all.setDividerLocation(0.72);
        log("ready  " + engine.models().listModels().size() + " models, " + engine.actions().size() + " actions");
    }

    // --- generic screens -----------------------------------------------------------------------------------

    private Model selectedModel() {
        return models.getSelectedValue();
    }

    private Record selectedRecord() {
        int row = table.getSelectedRow();
        return row < 0 ? null : shown.get(table.convertRowIndexToModel(row));
    }

    private void refresh() {
        Model model = selectedModel();
        if (model == null) {
            return;
        }
        shown = engine.list(model.id());
        List<String> columns = new ArrayList<>(model.fields().stream().map(Field::name).toList());
        columns.add("version");
        Object[][] rows = shown.stream().map(r -> {
            List<Object> row = new ArrayList<>();
            for (Field f : model.fields()) {
                row.add(r.values().getOrDefault(f.name(), ""));
            }
            row.add(r.version());
            return row.toArray();
        }).toArray(Object[][]::new);
        tableModel.setDataVector(rows, columns.toArray());
        models.repaint();

        actionButtons.removeAll();
        for (Action action : engine.actions()) {
            if (action.model().equals(model.id())) {
                JButton b = button(action.name(), () -> runAction(action));
                b.setToolTipText(action.description());
                actionButtons.add(b);
            }
        }
        actionButtons.revalidate();
        actionButtons.repaint();
        definition.setText(describe(model));
        definition.setCaretPosition(0);
    }

    private void createRecord() {
        Model model = selectedModel();
        Form form = new Form(model, null);
        if (form.ask("New " + model.displayName())) {
            attempt(() -> engine.create(model.id(), form.values()));
        }
    }

    private void editRecord() {
        Model model = selectedModel();
        Record record = selectedRecord();
        if (record == null) {
            log("pick a row first");
            return;
        }
        Form form = new Form(model, record.values());
        if (form.ask("Edit " + record.key())) {
            Map<String, Object> changes = new LinkedHashMap<>();
            for (Field f : model.fields()) {
                changes.put(f.name(), form.values().get(f.name())); // null clears the field
            }
            attempt(() -> engine.update(model.id(), record.id(), changes, record.version()));
        }
    }

    private void deleteRecord() {
        Record record = selectedRecord();
        if (record == null) {
            log("pick a row first");
            return;
        }
        attempt(() -> {
            engine.delete(record.model(), record.id());
            return null;
        });
    }

    private void runAction(Action action) {
        Record record = action.target() == Action.Target.EXISTING ? selectedRecord() : null;
        if (action.target() == Action.Target.EXISTING && record == null) {
            log("pick a row to run " + action.name() + " on");
            return;
        }
        Map<String, Object> input = Map.of();
        if (!action.input().isEmpty()) {
            Model inputModel = Model.valueObject(action.name()).id("action-input").fields(action.input()).build();
            Form form = new Form(inputModel, null);
            if (!form.ask(action.name() + (record == null ? "" : " on " + record.key()))) {
                return;
            }
            input = form.values();
        }
        Map<String, Object> finalInput = input;
        attempt(() -> engine.run(action.name(), record == null ? null : record.id(), finalInput));
    }

    /** Runs a platform call; shows refusals instead of crashing. */
    private void attempt(Supplier<Record> call) {
        try {
            Record r = call.get();
            if (r != null) {
                log("saved  " + r.key() + " v" + r.version() + "  " + r.values());
            }
        } catch (SchemaException e) {
            log("REFUSED (" + e.reason() + ")");
            e.problems().forEach(p -> log("   - " + p.message()));
            JOptionPane.showMessageDialog(table, String.join("\n", e.problems().stream()
                    .map(p -> "• " + p.message()).toList()), "Refused: " + e.reason(), JOptionPane.WARNING_MESSAGE);
        }
        refresh();
    }

    private String describe(Model model) {
        StringBuilder s = new StringBuilder();
        s.append(model.kind()).append(' ').append(model.displayName()).append("  [").append(model.id()).append("]\n");
        if (!model.description().isEmpty()) {
            s.append(model.description()).append('\n');
        }
        s.append('\n');
        for (Field f : model.fields()) {
            s.append("  ").append(f.name()).append(" : ").append(f.type());
            if (f.type() == FieldType.MODEL) {
                s.append('(').append(f.model()).append(')');
            }
            if (f.type() == FieldType.CHOICE) {
                s.append(f.choices());
            }
            if (f.identity()) {
                s.append("  identity");
            }
            if (f.required()) {
                s.append("  required");
            }
            if (!f.pattern().isEmpty()) {
                s.append("  /").append(f.pattern()).append('/');
            }
            s.append('\n');
        }
        for (Action action : engine.actions()) {
            if (!action.model().equals(model.id())) {
                continue;
            }
            s.append("\nACTION ").append(action.name()).append(action.target() == Action.Target.NEW ? " (creates)" : "")
                    .append('\n');
            action.input().forEach(f -> s.append("  input ").append(f.name()).append(" : ").append(f.type()).append('\n'));
            action.rules().forEach(r -> s.append("  require ").append(r.expression()).append('\n'));
            for (Step step : action.steps()) {
                switch (step) {
                    case Step.Set set -> s.append("  set ").append(set.field()).append(" = ").append(set.expression());
                    case Step.Call call -> s.append("  call ").append(call.handler());
                    case Step.Emit emit -> s.append("  emit ").append(emit.event());
                }
                s.append('\n');
            }
        }
        engine.models().analyze().stream().filter(f -> f.model().equals(model.id()))
                .forEach(f -> s.append("\nnote: ").append(f.message()));
        return s.toString();
    }

    /** A form generated from a model's fields; value objects become nested sections. */
    private final class Form {
        private final Model model;
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final Map<String, Supplier<Object>> readers = new LinkedHashMap<>();

        Form(Model model, Map<String, Object> current) {
            this.model = model;
            build(model, current == null ? Map.of() : current, readers, 0);
        }

        @SuppressWarnings("unchecked")
        private int build(Model model, Map<String, Object> current, Map<String, Supplier<Object>> into, int row) {
            for (Field f : model.fields()) {
                Object value = current.get(f.name());
                String label = f.name() + (f.required() ? " *" : "") + (f.identity() ? "  (id)" : "");
                if (f.type() == FieldType.MODEL && !f.repeated()) {
                    Model target = engine.models().getModel(f.model()).orElseThrow();
                    if (target.isValueObject()) {
                        JLabel section = new JLabel(f.name() + " : " + target.displayName());
                        section.setFont(section.getFont().deriveFont(Font.BOLD));
                        add(section, null, row++);
                        Map<String, Supplier<Object>> nested = new LinkedHashMap<>();
                        row = build(target, value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(), nested, row);
                        into.put(f.name(), () -> {
                            Map<String, Object> out = new LinkedHashMap<>();
                            nested.forEach((k, r) -> {
                                Object v = r.get();
                                if (v != null) {
                                    out.put(k, v);
                                }
                            });
                            return out.isEmpty() ? null : out;
                        });
                        continue;
                    }
                    // an entity: pick one of its records
                    JComboBox<String> box = new JComboBox<>();
                    box.addItem("");
                    engine.list(target.id()).forEach(r -> box.addItem(r.id()));
                    box.setSelectedItem(value == null ? "" : value);
                    add(new JLabel(label), box, row++);
                    into.put(f.name(), () -> blankToNull((String) box.getSelectedItem()));
                    continue;
                }
                switch (f.type()) {
                    case BOOLEAN -> {
                        JCheckBox box = new JCheckBox();
                        box.setSelected(Boolean.TRUE.equals(value));
                        add(new JLabel(label), box, row++);
                        into.put(f.name(), box::isSelected);
                    }
                    case CHOICE -> {
                        JComboBox<String> box = new JComboBox<>();
                        box.addItem("");
                        f.choices().forEach(box::addItem);
                        box.setSelectedItem(value == null ? "" : value);
                        add(new JLabel(label), box, row++);
                        into.put(f.name(), () -> blankToNull((String) box.getSelectedItem()));
                    }
                    default -> {
                        JTextField text = new JTextField(value == null ? "" : String.valueOf(value), 24);
                        String hint = switch (f.type()) {
                            case DATE -> "2026-10-18";
                            case TIMESTAMP -> "2026-10-18T14:00:00Z";
                            case INTEGER -> "whole number";
                            case DECIMAL -> "number";
                            default -> f.pattern().isEmpty() ? "" : "/" + f.pattern() + "/";
                        };
                        text.putClientProperty("JTextField.placeholderText", f.repeated() ? "comma, separated" : hint);
                        add(new JLabel(label), text, row++);
                        into.put(f.name(), () -> {
                            String s = text.getText().strip();
                            if (s.isEmpty()) {
                                return null;
                            }
                            return f.repeated() ? Arrays.stream(s.split(",")).map(String::strip).toList() : s;
                        });
                    }
                }
            }
            return row;
        }

        private void add(JComponent label, Component field, int row) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row;
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;
            if (field == null) {
                c.gridwidth = 2;
                c.insets = new Insets(10, 4, 2, 4);
                panel.add(label, c);
                return;
            }
            c.gridx = 0;
            panel.add(label, c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            panel.add(field, c);
        }

        boolean ask(String title) {
            return JOptionPane.showConfirmDialog(table, new JScrollPane(panel), title, JOptionPane.OK_CANCEL_OPTION,
                                                 JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
        }

        Map<String, Object> values() {
            Map<String, Object> out = new LinkedHashMap<>();
            readers.forEach((k, r) -> {
                Object v = r.get();
                if (v != null) {
                    out.put(k, v);
                }
            });
            return out;
        }
    }

    // --- small helpers -------------------------------------------------------------------------------------

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JLabel header(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return l;
    }

    private void log(String line) {
        log.append(LocalTime.now().withNano(0) + "  " + line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private void toggleTheme() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getLookAndFeel() instanceof FlatDarkLaf
                                                 ? new FlatLightLaf() : new FlatDarkLaf());
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }
        } catch (Exception e) {
            log("theme: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new PlatformDemo().show());
    }
}

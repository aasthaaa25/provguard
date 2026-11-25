package com.provguard.desktop;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReviewApp extends Application {
    private static final Logger LOG = Logger.getLogger(ReviewApp.class.getName());
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int HISTORY_LIMIT = 8;

    private ReviewSettings settings;
    private CaseStore store;
    private Stage stage;
    private BorderPane view;
    private final TableView<ReviewCase> cases = new TableView<>();
    private final TextField search = new TextField();
    private final TextField title = new TextField();
    private final TextArea notes = new TextArea();
    private final ComboBox<ReviewCase.Status> status = new ComboBox<>();
    private final CheckBox showClosed = new CheckBox("Include closed");
    private final Label summary = new Label();
    private final Label feedback = new Label("Ready");
    private final ListView<String> activity = new ListView<>();
    private final LinkedList<String> history = new LinkedList<>();
    private final Button delete = new Button("Delete case");
    private UUID editing;

    @Override
    public void init() throws Exception {
        settings = ReviewSettings.load();
        store = new SqliteCaseStore(settings.database());
        LOG.info("Review database opened.");
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        view = new BorderPane();
        view.setPadding(new Insets(18));
        view.setTop(buildHeader());
        view.setCenter(new StackPane(cases));
        view.setRight(buildEditor());
        view.setBottom(feedback);
        BorderPane.setMargin(cases.getParent(), new Insets(14, 14, 14, 0));
        cases.getColumns().add(column("Case", ReviewCase::title, 250));
        cases.getColumns().add(column("Status", item -> item.status().toString(), 105));
        cases.getColumns().add(column("Source", ReviewCase::source, 110));
        cases.getColumns().add(column("Opened", item -> item.openedAt().format(DISPLAY_TIME), 125));
        cases.setPlaceholder(new Label("No cases match the current filters."));
        cases.getSelectionModel().selectedItemProperty().addListener((observable, before, selected) -> {
            if (selected != null && !selected.id().equals(editing)) {
                perform(() -> store.find(selected.id()).ifPresent(this::edit));
            }
        });
        search.textProperty().addListener((observable, before, after) -> perform(() -> refresh(editing)));
        showClosed.selectedProperty().addListener((observable, before, after) -> perform(() -> refresh(editing)));
        stage.setTitle(settings.title());
        stage.setScene(new Scene(view, 1020, 655));
        stage.setMinWidth(880);
        stage.setMinHeight(560);
        newCase();
        perform(() -> refresh(null));
        stage.show();
    }

    private VBox buildHeader() {
        var heading = new Label("ProvGuard Case Review");
        heading.setFont(javafx.scene.text.Font.font(25));
        search.setPromptText("Search case title or notes");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        showClosed.setSelected(true);
        var fresh = new Button("New case");
        fresh.setOnAction(event -> newCase());
        var importButton = new Button("Import event log");
        importButton.setOnAction(event -> chooseImport());
        return new VBox(10, heading, new HBox(10, search, showClosed, fresh, importButton), summary);
    }

    private VBox buildEditor() {
        status.getItems().setAll(ReviewCase.Status.values());
        status.setMaxWidth(Double.MAX_VALUE);
        notes.setWrapText(true);
        notes.setPrefRowCount(5);
        var form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Title"), title);
        form.addRow(1, new Label("Status"), status);
        form.addRow(2, new Label("Notes"), notes);
        GridPane.setHgrow(title, Priority.ALWAYS);
        var save = new Button("Save case");
        save.setDefaultButton(true);
        save.setOnAction(event -> perform(this::save));
        delete.setOnAction(event -> perform(this::delete));
        activity.setPrefHeight(140);
        var editor = new VBox(10, new Label("Case details"), form, new HBox(8, save, delete),
                new Label("Recent actions"), activity);
        editor.setPrefWidth(325);
        BorderPane.setMargin(editor, new Insets(14, 0, 14, 0));
        return editor;
    }

    private static <T> TableColumn<T, String> column(String label, Function<T, String> value, double width) {
        var column = new TableColumn<T, String>(label);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private void refresh(UUID selected) throws ReviewException {
        var all = store.list();
        Map<ReviewCase.Status, Integer> counts = new HashMap<>();
        LocalDate today = LocalDate.now();
        int openedToday = 0;
        for (var iterator = all.iterator(); iterator.hasNext();) {
            ReviewCase item = iterator.next();
            counts.merge(item.status(), 1, Integer::sum);
            if (item.openedAt().toLocalDate().equals(today)) {
                openedToday++;
            }
        }
        String term = search.getText().strip().toLowerCase(Locale.ROOT);
        var visible = all.stream()
                .filter(item -> showClosed.isSelected() || item.status() != ReviewCase.Status.CLOSED)
                .filter(item -> item.title().toLowerCase(Locale.ROOT).contains(term)
                        || item.notes().toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing(ReviewCase::openedAt).reversed().thenComparing(ReviewCase::id))
                .toList();
        cases.setItems(FXCollections.observableArrayList(visible));
        visible.stream().filter(item -> item.id().equals(selected)).findFirst()
                .ifPresent(cases.getSelectionModel()::select);
        summary.setText("%d shown  ·  %d opened today  ·  %d open  ·  %d in review  ·  %d closed".formatted(
                visible.size(), openedToday,
                counts.getOrDefault(ReviewCase.Status.OPEN, 0), counts.getOrDefault(ReviewCase.Status.IN_REVIEW, 0),
                counts.getOrDefault(ReviewCase.Status.CLOSED, 0)));
    }

    private void edit(ReviewCase item) {
        editing = item.id();
        title.setText(item.title());
        status.setValue(item.status());
        notes.setText(item.notes());
        delete.setDisable(false);
    }

    private void newCase() {
        editing = null;
        cases.getSelectionModel().clearSelection();
        title.clear();
        notes.clear();
        status.setValue(ReviewCase.Status.OPEN);
        delete.setDisable(true);
        title.requestFocus();
    }

    private void save() throws ReviewException {
        boolean creating = editing == null;
        var original = creating
                ? null
                : store.find(editing).orElseThrow(() -> new IllegalArgumentException("Case no longer exists."));
        ReviewCase item = new ReviewCase(creating ? UUID.randomUUID() : original.id(),
                creating ? LocalDateTime.now() : original.openedAt(),
                title.getText(), creating ? "Manual" : original.source(),
                Objects.requireNonNull(status.getValue()), notes.getText());
        if (creating) {
            store.insert(item);
        } else {
            store.update(item);
        }
        edit(item);
        refresh(item.id());
        record(creating ? "Case created" : "Case updated");
    }

    private void delete() throws ReviewException {
        UUID id = editing;
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete this case from the review database?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(stage);
        if (id != null && confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            store.delete(id);
            newCase();
            refresh(null);
            record("Case deleted");
        }
    }

    private void chooseImport() {
        var chooser = new FileChooser();
        chooser.setTitle("Choose ProvGuard JSONL events");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSONL event log", "*.jsonl"));
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            importFile(file.toPath());
        }
    }

    private void importFile(Path path) {
        var task = new Task<Integer>() {
            @Override
            protected Integer call() throws ReviewException {
                return store.importCases(EventCaseImporter.read(path));
            }
        };
        view.setDisable(true);
        feedback.setText("Importing events…");
        task.setOnSucceeded(event -> {
            view.setDisable(false);
            perform(() -> {
                refresh(editing);
                record(task.getValue() + " new cases imported");
            });
        });
        task.setOnFailed(event -> {
            view.setDisable(false);
            showError(task.getException());
        });
        Thread.ofVirtual().name("provguard-review-import").start(task);
    }

    private void record(String message) {
        history.addFirst(LocalDateTime.now().format(DISPLAY_TIME) + "  " + message);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
        activity.getItems().setAll(history);
        feedback.setText(message);
        LOG.info(message);
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws ReviewException;
    }

    private void perform(Operation action) {
        try {
            action.run();
        } catch (ReviewException | IllegalArgumentException exception) {
            showError(exception);
        }
    }

    private void showError(Throwable error) {
        LOG.log(Level.WARNING, error.getMessage(), error);
        feedback.setText("Operation failed");
        var alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        LOG.info("Case review closed.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

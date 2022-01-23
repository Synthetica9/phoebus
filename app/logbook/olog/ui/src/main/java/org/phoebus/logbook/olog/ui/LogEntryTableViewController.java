package org.phoebus.logbook.olog.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.phoebus.framework.jobs.JobManager;
import org.phoebus.logbook.LogClient;
import org.phoebus.logbook.LogEntry;
import org.phoebus.logbook.LogbookException;
import org.phoebus.logbook.Property;
import org.phoebus.logbook.SearchResult;
import org.phoebus.logbook.olog.ui.LogbookQueryUtil.Keys;
import org.phoebus.logbook.olog.ui.query.OlogQuery;
import org.phoebus.logbook.olog.ui.query.OlogQueryManager;
import org.phoebus.olog.es.api.model.LogGroupProperty;
import org.phoebus.ui.dialog.DialogHelper;
import org.phoebus.ui.javafx.ImageCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A controller for a log entry table with a collapsible advance search section.
 *
 * @author Kunal Shroff
 */
public class LogEntryTableViewController extends LogbookSearchController {

    @FXML
    private Button resize;
    @FXML
    private ComboBox<OlogQuery> query;

    // elements associated with the various search
    @FXML
    private GridPane ViewSearchPane;

    // elements related to the table view of the log entries
    @FXML
    private TableView<LogEntry> tableView;
    @FXML
    private TableColumn<LogEntry, LogEntry> descriptionCol;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private LogEntryDisplayController logEntryDisplayController;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    @SuppressWarnings({"UnusedDeclaration"})
    private AdvancedSearchViewController advancedSearchViewController;

    @FXML
    private ImageView searchDescendingImageView;
    @FXML
    private ImageView searchAscendingImageView;

    @FXML
    private Pagination pagination;
    @FXML
    private Node searchResultView;

    @FXML
    private TextField pageSizeTextField;
    // Model
    private SearchResult searchResult;

    // Search parameters
    ObservableMap<Keys, String> searchParameters = FXCollections.observableHashMap();

    /**
     * List of selected log entries
     */
    private final ObservableList<LogEntry> selectedLogEntries = FXCollections.observableArrayList();
    private final Logger logger = Logger.getLogger(LogEntryTableViewController.class.getName());

    /**
     * Constructor.
     *
     * @param logClient Log client implementation
     */
    public LogEntryTableViewController(LogClient logClient) {
        setClient(logClient);
    }

    private final SimpleBooleanProperty searchInProgress = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty sortAscending = new SimpleBooleanProperty(false);

    private final SimpleIntegerProperty hitCountProperty = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty pageSizeProperty =
            new SimpleIntegerProperty(LogbookUIPreferences.search_result_page_size);
    private final SimpleIntegerProperty pageCountProperty = new SimpleIntegerProperty(0);
    private final OlogQueryManager ologQueryManager = OlogQueryManager.getInstance();
    private final ObservableList<OlogQuery> ologQueries = FXCollections.observableArrayList();

    /**
     * The listener called when user selects an item in the {@link ComboBox}
     * drop-down, or when a value is set implicitly from code when updating the list of items in the drop-down.
     */
    private ChangeListener<OlogQuery> onActionListener;

    private SearchParameters searchParams = new SearchParameters();


    @FXML
    public void initialize() {
        configureComboBox();
        // Set the search parameters in the advanced search controller so that it operates on the same object.
        advancedSearchViewController.setSearchParameters(searchParameters);
        advancedSearchViewController.setSearchParameters(searchParams);
        ologQueries.setAll(ologQueryManager.getQueries());

        /*
        searchParameters.addListener((MapChangeListener<Keys, String>) change -> query.getEditor().setText(searchParameters.entrySet().stream()
                .sorted(Entry.comparingByKey())
                .map((e) -> e.getKey().getName().trim() + "=" + e.getValue().trim())
                .collect(Collectors.joining("&"))));

         */

        searchParams.addListener((observable, oldValue, newValue) -> {
            System.out.println();
        });

        MenuItem groupSelectedEntries = new MenuItem(Messages.GroupSelectedEntries);
        groupSelectedEntries.setOnAction(e -> {
            createLogEntryGroup();
        });
        groupSelectedEntries.disableProperty()
                .bind(Bindings.createBooleanBinding(() ->
                        selectedLogEntries.size() < 2, selectedLogEntries));
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().add(groupSelectedEntries);

        // The display table.
        tableView.getColumns().clear();
        tableView.setEditable(false);
        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> logEntryDisplayController.setLogEntry(newValue));

        tableView.getStylesheets().add(this.getClass().getResource("/search_result_view.css").toExternalForm());

        descriptionCol = new TableColumn<>();
        descriptionCol.setMaxWidth(1f * Integer.MAX_VALUE * 100);
        descriptionCol.setCellValueFactory(col -> new SimpleObjectProperty(col.getValue()));
        descriptionCol.setCellFactory(col -> {
            return new TableCell<>() {
                private final Node graphic;
                private final PseudoClass childlessTopLevel =
                        PseudoClass.getPseudoClass("grouped");
                private final LogEntryCellController controller;

                {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("LogEntryCell.fxml"));
                        graphic = loader.load();
                        controller = loader.getController();
                    } catch (IOException exc) {
                        throw new RuntimeException(exc);
                    }
                }

                @Override
                public void updateItem(LogEntry logEntry, boolean empty) {
                    super.updateItem(logEntry, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        controller.setLogEntry(logEntry);
                        setGraphic(graphic);
                        boolean b = LogGroupProperty.getLogGroupProperty(logEntry).isPresent();
                        pseudoClassStateChanged(childlessTopLevel, b);
                    }
                }
            };
        });

        tableView.getColumns().add(descriptionCol);
        tableView.setPlaceholder(new Label(Messages.NoSearchResults));

        progressIndicator.visibleProperty().bind(searchInProgress);

        searchDescendingImageView.setImage(ImageCache.getImage(LogEntryTableViewController.class, "/icons/arrow_down.png"));
        searchAscendingImageView.setImage(ImageCache.getImage(LogEntryTableViewController.class, "/icons/arrow_up.png"));
        searchResultView.disableProperty().bind(searchInProgress);

        pagination.currentPageIndexProperty().addListener((a, b, c) -> {
            search();
        });

        pageSizeTextField.setText(Integer.toString(pageSizeProperty.get()));

        Pattern DIGIT_PATTERN = Pattern.compile("\\d*");
        // This is to accept numerical input only, and at most 3 digits (maximizing search to 999 hits).
        pageSizeTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (DIGIT_PATTERN.matcher(newValue).matches()) {
                if ("".equals(newValue)) {
                    pageSizeProperty.set(LogbookUIPreferences.search_result_page_size);
                } else if (newValue.length() > 3) {
                    pageSizeTextField.setText(oldValue);
                } else {
                    pageSizeProperty.set(Integer.parseInt(newValue));
                }
            } else {
                pageSizeTextField.setText(oldValue);
            }
        });

        // Hide the pagination widget if hit count == 0 or page count < 2
        pagination.visibleProperty().bind(Bindings.createBooleanBinding(() -> hitCountProperty.get() > 0 && pagination.pageCountProperty().get() > 1,
                hitCountProperty, pagination.pageCountProperty()));
        pagination.pageCountProperty().bind(pageCountProperty);
        pagination.maxPageIndicatorCountProperty().bind(pageCountProperty);

        query.itemsProperty().bind(new SimpleObjectProperty<>(ologQueries));

        // NOTE: the listener will ensure that whenever user chooses a query from the drop-down,
        // or when the selected query is set in code, a new search is triggered.
        onActionListener = (observable, oldValue, newValue) -> search();

        query.getSelectionModel().selectedItemProperty().addListener(onActionListener);
        query.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                // Query set -> search is triggered!
                query.setValue(new OlogQuery(query.getEditor().getText()));
            }
        });
        query.getEditor().setText(ologQueries.get(0).getQuery());
        // Query set -> search is triggered!
        query.getSelectionModel().select(ologQueries.get(0));
        searchParams.setQuery(ologQueries.get(0).getQuery());
    }

    // Keeps track of when the animation is active. Multiple clicks will be ignored
    // until a give resize action is completed
    private final AtomicBoolean moving = new AtomicBoolean(false);

    @FXML
    public void resize() {
        if (!moving.compareAndExchangeAcquire(false, true)) {
            if (resize.getText().equals("<")) {
                query.disableProperty().set(false);
                Duration cycleDuration = Duration.millis(400);
                KeyValue kv = new KeyValue(advancedSearchViewController.getPane().minWidthProperty(), 0);
                KeyValue kv2 = new KeyValue(advancedSearchViewController.getPane().maxWidthProperty(), 0);
                Timeline timeline = new Timeline(new KeyFrame(cycleDuration, kv, kv2));
                timeline.play();
                timeline.setOnFinished(event -> {
                    resize.setText(">");
                    moving.set(false);
                    advancedSearchViewController.updateSearchParametersFromInput();
                    search();
                });
            } else {
                Duration cycleDuration = Duration.millis(400);
                double width = ViewSearchPane.getWidth() / 2.5;
                KeyValue kv = new KeyValue(advancedSearchViewController.getPane().minWidthProperty(), width);
                KeyValue kv2 = new KeyValue(advancedSearchViewController.getPane().prefWidthProperty(), width);
                Timeline timeline = new Timeline(new KeyFrame(cycleDuration, kv, kv2));
                timeline.play();
                timeline.setOnFinished(event -> {
                    resize.setText("<");
                    moving.set(false);
                    query.disableProperty().set(true);
                    searchParams.setQuery(query.getEditor().getText());
                    advancedSearchViewController.updateSearchParamsFromQueryString(query.getEditor().getText());
                });
            }
        }
    }

    @FXML
    public void searchDescending() {
        sortAscending.set(false);
        search();
    }

    @FXML
    public void searchAscending() {
        sortAscending.set(true);
        search();
    }

    public void search() {
        // In case the page size text field is empty, or the value is zero, set the page size to the default
        if ("".equals(pageSizeTextField.getText()) || Integer.parseInt(pageSizeTextField.getText()) == 0) {
            pageSizeTextField.setText(Integer.toString(LogbookUIPreferences.search_result_page_size));
        }

        // Need to remove the listener as a new search would be invoked when combo box list is updated
        // with the refreshed list of queries
        query.getSelectionModel().selectedItemProperty().removeListener(onActionListener);
        tableView.getSelectionModel().clearSelection();

        OlogQuery ologQuery = ologQueryManager.getOrAddQuery(query.getEditor().getText());

        // Construct the query parameters from the search field string. Note that some keys
        // are treated as "hidden" and removed in the returned map.
        Map<String, String> params = LogbookQueryUtil.parseHumanReadableQueryString(ologQuery.getQuery());
        searchParameters.clear();
        params.entrySet().forEach(e -> searchParameters.put(Keys.findKey(e.getKey()), e.getValue()));

        params.put("sort", sortAscending.get() ? "up" : "down");
        params.put("from", Integer.toString(pagination.getCurrentPageIndex() * pageSizeProperty.get()));
        params.put("size", Integer.toString(pageSizeProperty.get()));

        searchInProgress.set(true);

        super.search(params, (inProgress) -> {
            searchInProgress.set(inProgress);
            List<OlogQuery> queries = ologQueryManager.getQueries();
            Platform.runLater(() -> {
                ologQueries.setAll(queries);
                // Top-most query is the one used in the search.
                query.getSelectionModel().select(ologQueries.get(0));
                // Add the listener
                query.getSelectionModel().selectedItemProperty().addListener(onActionListener);
            });
        });
    }

    @Override
    public void setLogs(List<LogEntry> logs) {
        throw new RuntimeException(new UnsupportedOperationException());
    }

    @Override
    public void setSearchResult(SearchResult searchResult) {
        this.searchResult = searchResult;
        hitCountProperty.set(searchResult.getHitCount());
        refresh();
    }

    public void setQuery(String parsedQuery) {
        Map<String, String> params = LogbookQueryUtil.parseHumanReadableQueryString(parsedQuery);
        searchParameters.clear();
        params.forEach((key, value) -> searchParameters.put(Keys.findKey(key), value));
        search();
    }

    public String getQuery() {
        return query.getValue().getQuery();
    }

    private void refresh() {
        if (this.searchResult != null) {
            ObservableList<LogEntry> logsList = FXCollections.observableArrayList();
            logsList.addAll(new ArrayList<>(searchResult.getLogs()));
            tableView.setItems(logsList);
            hitCountProperty.set(searchResult.getHitCount());
            pageCountProperty.set(1 + (hitCountProperty.get() / pageSizeProperty.get()));
        }
    }

    private void createLogEntryGroup() {
        try {
            Property logEntryGroupProperty = LogGroupProperty.getLogEntryGroupProperty(selectedLogEntries);
            // Update all log entries asynchronously
            JobManager.schedule("Update log entries", monitor -> {
                selectedLogEntries.forEach(l -> {
                    // Update only if log entry does not contains the log group property
                    if (LogGroupProperty.getLogGroupProperty(l).isEmpty()) {
                        l.getProperties().add(logEntryGroupProperty);
                        try {
                            getClient().updateLogEntry(l);
                        } catch (LogbookException e) {
                            logger.log(Level.SEVERE, "Failed to update log entry " + l.getId(), e);
                        }
                    }
                });
                // When all log entries are updated, run the search to trigger an update of the UI
                search();
            });
        } catch (LogbookException e) {
            logger.log(Level.INFO, "Unable to create log entry group from selection");
            final Alert dialog = new Alert(AlertType.INFORMATION);
            dialog.setHeaderText("Cannot create log entry group. Selected list of log entries references more than one existing group.");
            DialogHelper.positionDialog(dialog, tableView /*treeView*/, 0, 0);
            dialog.showAndWait();
        }
    }

    @FXML
    @SuppressWarnings("unused")
    public void goToFirstPage() {
        pagination.setCurrentPageIndex(0);
    }

    @FXML
    @SuppressWarnings("unused")
    public void goToLastPage() {
        pagination.setCurrentPageIndex(pagination.pageCountProperty().get() - 1);
    }

    private void configureComboBox(){
        Font defaultQueryFont = Font.font("Liberation Sans", FontWeight.BOLD, 12);
        Font defaultQueryFontRegular = Font.font("Liberation Sans", FontWeight.NORMAL, 12);
        query.setVisibleRowCount(OlogQueryManager.getInstance().getQueryListSize());
        // Needed to customize item rendering, e.g. default query rendered in bold.
        query.setCellFactory(
                new Callback<>() {
                    @Override
                    public ListCell<OlogQuery> call(ListView<OlogQuery> param) {
                        final ListCell<OlogQuery> cell = new ListCell<>() {
                            @Override
                            public void updateItem(OlogQuery item,
                                                   boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null) {
                                    setText(item.getQuery().isEmpty() ? "<empty>" : item.getQuery());
                                    if (item.isDefaultQuery()) {
                                        setFont(defaultQueryFont);
                                    } else {
                                        setFont(defaultQueryFontRegular);
                                    }
                                } else {
                                    setText(null);
                                }
                            }
                        };
                        return cell;
                    }
                });

        // This is needed for the "editor" part of the ComboBox
        query.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(OlogQuery query) {
                        if (query == null) {
                            return "";
                        } else {
                            return query.getQuery();
                        }
                    }
                    @Override
                    public OlogQuery fromString(String s) {
                        return new OlogQuery(s);
                    }
                });

    }
}

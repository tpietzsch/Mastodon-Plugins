package de.mpicbg.tomancaklab;

import org.mastodon.adapter.FocusModelAdapter;
import org.mastodon.adapter.HighlightModelAdapter;
import org.mastodon.adapter.RefBimap;
import org.mastodon.adapter.SelectionModelAdapter;
import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.model.*;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.project.MamutProject;
import org.mastodon.project.MamutProjectIO;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.mamut.Mastodon;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelGraph;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.trackscheme.TrackSchemeEdge;
import org.mastodon.revised.trackscheme.TrackSchemeGraph;
import org.mastodon.revised.trackscheme.TrackSchemeVertex;
import org.mastodon.revised.trackscheme.display.TrackSchemeNavigationActions;
import org.mastodon.revised.trackscheme.display.TrackSchemeOptions;
import org.mastodon.revised.trackscheme.display.TrackSchemePanel;
import org.mastodon.revised.trackscheme.display.TrackSchemeZoom;
import org.mastodon.revised.trackscheme.wrap.DefaultModelGraphProperties;
import org.mastodon.revised.ui.FocusActions;
import org.mastodon.revised.ui.keymap.Keymap;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.scijava.AbstractContextual;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.util.*;

import javax.swing.*;
import java.util.*;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

@Plugin(type = ConvertTimeToGenerations.class)
public class ConvertTimeToGenerations extends AbstractContextual implements MastodonPlugin {

    private static final String PluginID = "Convert Time To Generations";
    //------------------------------------------------------------------------

    @Override
    public List<ViewMenuBuilder.MenuItem> getMenuItems() {
        return Arrays.asList(
                menu("Plugins",
                        item(PluginID)));
    }

    private static Map<String, String> menuTexts = new HashMap<>();

    static {
        menuTexts.put(PluginID, "Convert Time To Generations");
    }

    @Override
    public Map<String, String> getMenuTexts() {
        return menuTexts;
    }

    private final AbstractNamedAction ConvertTimeToGenerations;

    public ConvertTimeToGenerations() {
        ConvertTimeToGenerations = new RunnableAction(PluginID, this::saveNodesAndTracks);
        updateEnabledActions();
    }

    @Override
    public void installGlobalActions(final Actions actions) {
        final String[] noShortCut = new String[]{};
        actions.namedAction(ConvertTimeToGenerations, noShortCut);
    }

    private MastodonPluginAppModel pluginAppModel;

    @Override
    public void setAppModel(final MastodonPluginAppModel model) {
        this.pluginAppModel = model;
        updateEnabledActions();
    }

    private void updateEnabledActions() {
        final MamutAppModel appModel = (pluginAppModel == null) ? null : pluginAppModel.getAppModel();
        ConvertTimeToGenerations.setEnabled(appModel != null);
    }

    private LogService logServiceRef;

    private synchronized void saveNodesAndTracks() {
        logServiceRef = this.getContext().getService(LogService.class).log();
        logServiceRef.trace("Saved the tracked data in a new format ...");
        final Model model = pluginAppModel.getAppModel().getModel();
        ModelGraph modelGraph = model.getGraph();


        final SpatioTemporalIndex<Spot> spots = model.getSpatioTemporalIndex();
        showModelGraph(modelGraph);


    }


    private void showModelGraph(ModelGraph modelGraph) {

        TrackSchemeGraph<Spot, Link> trackSchemeGraph = new TrackSchemeGraph<>(
                modelGraph,
                modelGraph.getGraphIdBimap(),
                new DefaultModelGraphProperties<>(),
                modelGraph.getLock());

        final RefBimap<Spot, TrackSchemeVertex> vertexMap = trackSchemeGraph.getVertexMap();
        final RefBimap<Link, TrackSchemeEdge> edgeMap = trackSchemeGraph.getEdgeMap();

        final HighlightModel<TrackSchemeVertex, TrackSchemeEdge> highlightModel =
                new HighlightModelAdapter<>(
                        new DefaultHighlightModel<>(modelGraph.getGraphIdBimap()),
                        vertexMap, edgeMap);
        final FocusModel<TrackSchemeVertex, TrackSchemeEdge> focusModel =
                new FocusModelAdapter<>(
                        new DefaultFocusModel<>(modelGraph.getGraphIdBimap()),
                        vertexMap, edgeMap);
        final DefaultTimepointModel timepointModel = new DefaultTimepointModel();
        final SelectionModel<TrackSchemeVertex, TrackSchemeEdge> selectionModel =
                new SelectionModelAdapter<>(
                        new DefaultSelectionModel<>(modelGraph, modelGraph.getGraphIdBimap()),
                        vertexMap, edgeMap);
        final NavigationHandler<TrackSchemeVertex, TrackSchemeEdge> navigationHandler = new DefaultNavigationHandler<>();
        final AutoNavigateFocusModel<TrackSchemeVertex, TrackSchemeEdge> navigateFocusModel = new AutoNavigateFocusModel<>(focusModel, navigationHandler);

        TrackSchemeOptions trackSchemeOptions = new TrackSchemeOptions();

        TrackSchemePanel trackSchemePanel = new TrackSchemePanel(
                trackSchemeGraph,
                highlightModel,
                navigateFocusModel,
                timepointModel,
                selectionModel,
                navigationHandler,
                trackSchemeOptions);
        trackSchemePanel.setVisible(true);
        JFrame jFrame = new JFrame();
        jFrame.add(trackSchemePanel);
        jFrame.pack();

        InputActionBindings keybindings = new InputActionBindings();
        TriggerBehaviourBindings triggerbindings = new TriggerBehaviourBindings();

        SwingUtilities.replaceUIActionMap(jFrame.getRootPane(), keybindings.getConcatenatedActionMap());
        SwingUtilities.replaceUIInputMap(jFrame.getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap());

        MamutAppModel appModel = pluginAppModel.getAppModel();

        final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
        mouseAndKeyHandler.setInputMap(triggerbindings.getConcatenatedInputTriggerMap());
        mouseAndKeyHandler.setBehaviourMap(triggerbindings.getConcatenatedBehaviourMap());
        mouseAndKeyHandler.setKeypressManager(appModel.getKeyPressedManager(), trackSchemePanel.getDisplay());
        trackSchemePanel.getDisplay().addHandler(mouseAndKeyHandler);

        trackSchemePanel.setTimepointRange(0, 100);
        trackSchemePanel.graphChanged();

        Keymap keymap = appModel.getKeymap();
        String[] keyConfigContexts = new String[]{KeyConfigContexts.TRACKSCHEME};

        Actions viewActions = new Actions(keymap.getConfig(), keyConfigContexts);
        viewActions.install(keybindings, "view");

        Behaviours viewBehaviours = new Behaviours(keymap.getConfig(), keyConfigContexts);
        viewBehaviours.install(triggerbindings, "view");

        trackSchemePanel.getNavigationActions().install(viewActions, TrackSchemeNavigationActions.NavigatorEtiquette.FINDER_LIKE);
        trackSchemePanel.getNavigationBehaviours().install(viewBehaviours);
        trackSchemePanel.getTransformEventHandler().install(viewBehaviours);

        FocusActions.install(viewActions, trackSchemeGraph, trackSchemeGraph.getLock(), navigateFocusModel, selectionModel);
        TrackSchemeZoom.install(viewBehaviours, trackSchemePanel);

        jFrame.setVisible(true);
    }


    public static void main(String... args) throws Exception {


        final String projectPath = "/home/manan/Desktop/03_Datasets/13-03-12/Mastodon_w-lineage-ID";

        Locale.setDefault(Locale.US);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        final Mastodon mastodon = new Mastodon();
        new Context().inject(mastodon);
        mastodon.run();

        final MamutProject project = new MamutProjectIO().load(projectPath);
        mastodon.openProject(project);
    }

}
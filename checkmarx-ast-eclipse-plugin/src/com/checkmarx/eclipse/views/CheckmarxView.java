package com.checkmarx.eclipse.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.utils.PluginConstants;
import com.checkmarx.eclipse.utils.PluginUtils;
import com.checkmarx.eclipse.views.actions.ActionOpenPreferencesPage;
import com.checkmarx.eclipse.views.actions.ToolBarActions;
import com.checkmarx.eclipse.views.provider.ColumnProvider;
import com.checkmarx.eclipse.views.provider.TreeContentProvider;

public class CheckmarxView extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.checkmarx.eclipse.views.CheckmarxView";

	public static final Image CRITICAL_SEVERITY = Activator.getImageDescriptor("/icons/severity-critical.png")
			.createImage();

	public static final Image HIGH_SEVERITY = Activator.getImageDescriptor("/icons/severity-high.png").createImage();

	public static final Image MEDIUM_SEVERITY = Activator.getImageDescriptor("/icons/severity-medium.png")
			.createImage();

	public static final Image LOW_SEVERITY = Activator.getImageDescriptor("/icons/severity-low.png").createImage();

	public static final Image INFO_SEVERITY = Activator.getImageDescriptor("platform:/plugin/org.eclipse.ui/icons/full/obj16/info_tsk.png").createImage();
	
	
	IWorkbench workbench;

	private TreeViewer viewer;
	private ComboViewer scanIdComboViewer, projectComboViewer;
	private StringFieldEditor scanIdField;
	private DisplayModel rootModel;
	private Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

	private static final String RUNNING = "Retrieving the results for the scan id: %s .";
	private static final String PROJECT_COMBO_VIEWER_TEXT = "Select project";
	private static final String SCAN_COMBO_VIEWER_TEXT = "Select scan id";


	private boolean alreadyRunning = false;

	private IPropertyChangeListener stringChangeListener;


	Font boldFont;
	private Text typeValueText;
	private Text severityValueText;
	private Text statusValueText;
	private Text descriptionValueText;
	private Text attackVectorValueLinkText;

	private Composite topComposite;
	private Composite resultInfoCompositePanel , attackVectorCompositePanel;
	private Composite leftCompositePanel;
	
	private ToolBarActions toolBarActions;

	public CheckmarxView() {
		super();
				
	//	DisplayModel init = new DisplayModel.DisplayModelBuilder("Paste a scanId and hit play to fetch the results.").build();
		rootModel = new DisplayModel.DisplayModelBuilder("").build();
	//	rootModel.children.add(init);

	}

	@Override
	public void dispose() {
		super.dispose();
		boldFont.dispose();
	}

	@Override
	public void createPartControl(Composite parent) {
		createViewer(parent);
		createToolbar();
		createContextMenu();

	}

	private void createContextMenu() {
		MenuManager menuManager = new MenuManager("#PopupMenu");
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(CheckmarxView.this::fillContextMenu);
		Menu menu = menuManager.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);

		getSite().registerContextMenu(menuManager, viewer);
	}

	private void fillContextMenu(IMenuManager manager) {
		Action openPreferencesPageAction = new ActionOpenPreferencesPage(rootModel, viewer, alreadyRunning, scanIdField, shell).createAction();
		manager.add(openPreferencesPageAction);
	}

	private void createToolbar() {
		IToolBarManager toolBarManager = getViewSite().getActionBars().getToolBarManager();
		toolBarActions = new ToolBarActions(rootModel, viewer, alreadyRunning, scanIdField, resultInfoCompositePanel, attackVectorCompositePanel, leftCompositePanel, scanIdComboViewer, projectComboViewer);
		
		for(Action action : toolBarActions.getToolBarActions()) {
			toolBarManager.add(action);
		}
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	private void createViewer(Composite parent) {

		GridLayout parentLayout = new GridLayout();
		parentLayout.numColumns = 1;
		parentLayout.horizontalSpacing = 0;
		parentLayout.verticalSpacing = 0;
		parentLayout.marginHeight = 0;
		parentLayout.marginWidth = 0;
		parent.setLayout(parentLayout);
		
		//Top Bar Composite Panel
		topComposite = new Composite(parent, SWT.NONE);
		GridLayout topLayout = new GridLayout();
		topLayout.numColumns = 2;
		topComposite.setLayout(topLayout);
		
		GridData topGridData = new GridData();
		topGridData.horizontalAlignment = GridData.FILL;
		topGridData.verticalAlignment = GridData.FILL;
		topGridData.grabExcessHorizontalSpace = true;
		topComposite.setLayoutData(topGridData);
		createProjectListComboBox(topComposite);
		createScanIdComboBox(topComposite);
		
		//Bottom Panel
		Composite bottomComposite = new Composite(parent, SWT.BORDER);
		
		GridData bottomGridData = new GridData();
		bottomGridData.horizontalAlignment = GridData.FILL;
		bottomGridData.verticalAlignment = GridData.FILL;
		bottomGridData.grabExcessHorizontalSpace = true;
		bottomGridData.grabExcessVerticalSpace = true;
		
		FillLayout bottomLayout = new FillLayout(SWT.HORIZONTAL);
		bottomComposite.setLayout(bottomLayout);
		bottomComposite.setLayoutData(bottomGridData);
		
		leftCompositePanel = new Composite(bottomComposite, SWT.BORDER);
		GridLayout 	leftCompositeLayout = new GridLayout();
		leftCompositeLayout.numColumns = 1;
		GridData leftCompositePanelGridData = new GridData();
		leftCompositePanelGridData.horizontalAlignment = GridData.BEGINNING;
		leftCompositePanelGridData.grabExcessVerticalSpace = true;
		
		leftCompositePanel.setLayoutData(leftCompositePanelGridData);
		leftCompositePanel.setLayout(leftCompositeLayout);
		
		scanIdField = new StringFieldEditor("scanId", "Scan Id:", 36, leftCompositePanel);
		scanIdField.setTextLimit(36);
		scanIdField.setEmptyStringAllowed(false);

		scanIdField.getTextControl(leftCompositePanel).addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event event) {
				toolBarActions.getScanResultsAction().run();
			}
		});
		
		scanIdField.getTextControl(leftCompositePanel).setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
		
		viewer = new TreeViewer(leftCompositePanel, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		ColumnViewerToolTipSupport.enableFor(viewer);
		createColumns();

		viewer.getTree().setHeaderVisible(true);
		viewer.getTree().setLinesVisible(true);

		viewer.setContentProvider(new TreeContentProvider());
		getSite().setSelectionProvider(viewer);

		// define layout for the viewer
		GridData gridData = new GridData();
		gridData.verticalAlignment = GridData.FILL;
		gridData.horizontalSpan = 2;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;
		viewer.getControl().setLayoutData(gridData);
		viewer.setInput(rootModel);

		// configureSelectionListener
		configureTreeItemSelectionChangeEvent(viewer);

		// SECTION 2
		// Setting the BOLD Font for Labels
		Display display = parent.getShell().getDisplay();
		FontData systemFontData = display.getSystemFont().getFontData()[0];
		boldFont = new Font(display, systemFontData.getName(), systemFontData.getHeight(), SWT.BOLD);

		resultInfoCompositePanel = new Composite(bottomComposite, SWT.BORDER);
		resultInfoCompositePanel.setLayout(new FillLayout(SWT.VERTICAL));

		Label typeLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		typeLabel.setFont(boldFont);
		typeLabel.setText("Type:");

		typeValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		typeValueText.setText("Not Available.");

		Label severityLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		severityLabel.setFont(boldFont);
		severityLabel.setText("Severity:");

		severityValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		severityValueText.setText("Not Available.");

		Label statusLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		statusLabel.setFont(boldFont);
		statusLabel.setText("Status:");

		statusValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY);
		statusValueText.setText("Not Available.");

		Label descriptionLabel = new Label(resultInfoCompositePanel, SWT.NONE);
		descriptionLabel.setFont(boldFont);
		descriptionLabel.setText("Description:");

		descriptionValueText = new Text(resultInfoCompositePanel, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		descriptionValueText.setText("Not Available.");

		// Section 3
		attackVectorCompositePanel = new Composite(bottomComposite, SWT.BORDER);
		
		GridData attackVectorCompositePanelGridData = new GridData();
		attackVectorCompositePanelGridData.horizontalAlignment = GridData.END;
		attackVectorCompositePanelGridData.grabExcessHorizontalSpace = true;
		attackVectorCompositePanelGridData.grabExcessVerticalSpace = true;
		
		attackVectorCompositePanel.setLayoutData(attackVectorCompositePanelGridData);
		
		attackVectorCompositePanel.setLayout(new RowLayout(SWT.VERTICAL));

		Label attackVectorLabel = new Label(attackVectorCompositePanel, SWT.NONE);
		attackVectorLabel.setFont(boldFont);
		attackVectorLabel.setText("Attack Vector:");
		
		resultInfoCompositePanel.setVisible(false);
		attackVectorCompositePanel.setVisible(false);
	}
	
	private void createProjectListComboBox(Composite parent)
	{
		List<Project> projectList = DataProvider.INSTANCE.getProjectList();
		
		projectComboViewer = new ComboViewer(parent, SWT.DROP_DOWN);        
		projectComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		projectComboViewer.setInput(projectList); 
		
		GridData gridData = new GridData();
		gridData.widthHint = 400;
		projectComboViewer.getCombo().setLayoutData(gridData);
		
		projectComboViewer.setLabelProvider(new LabelProvider() {
		    @Override
		    public String getText(Object element) {
		        if (element instanceof Project) {
		            Project project = (Project) element;
		            return project.getName();
		        }
		        return super.getText(element);
		    }
		});
		
		
		projectComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
		    @Override
		    public void selectionChanged(SelectionChangedEvent event) {
		        IStructuredSelection selection = (IStructuredSelection) event
		            .getSelection();
		  		       
		        if (selection.size() > 0){
		        
	        	PluginUtils.enableComboViewer(scanIdComboViewer, false);	
	        	PluginUtils.setTextForComboViewer(scanIdComboViewer, "Getting scans for the project...");
			    scanIdComboViewer.getCombo().update();
		          
		         
		         Project selectedProject = ((Project)selection.getFirstElement());
		        
		         List<Scan> scanList = DataProvider.INSTANCE.getScanListOfProject(selectedProject.getID());
		        
		         if(scanList.isEmpty())
		         {
		        	 scanIdComboViewer.setInput(scanList);
		        	 PluginUtils.setTextForComboViewer(scanIdComboViewer, "No scans available.");   
		        	 PluginUtils.enableComboViewer(scanIdComboViewer, true);
		         }
		         else
		         {
		         scanIdComboViewer.setInput(scanList);
		         PluginUtils.setTextForComboViewer(scanIdComboViewer, SCAN_COMBO_VIEWER_TEXT);   
		         PluginUtils.enableComboViewer(scanIdComboViewer, true);
		         }
		         
		       
		         
		        }
		    }
		});
		
		PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);

	}

	private void createScanIdComboBox(Composite parent){
		
		scanIdComboViewer = new ComboViewer(parent, SWT.DROP_DOWN);        
		scanIdComboViewer.setContentProvider(ArrayContentProvider.getInstance());
			
		scanIdComboViewer.setLabelProvider(new LabelProvider() {
		    @Override
		    public String getText(Object element) {
		        if (element instanceof Scan) {
		            Scan scan = (Scan) element;
		            String updatedAtDate = PluginUtils.convertStringTimeStamp(scan.getUpdatedAt());
		            String itemLabel = scan.getID() + " ( " + scan.getStatus() + ", " + updatedAtDate + " )";
		            return itemLabel ;
		        }
		        return super.getText(element);
		    }
		});
		
		scanIdComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			
		    @Override
		    public void selectionChanged(SelectionChangedEvent event) {
		        IStructuredSelection selection = (IStructuredSelection) event
		            .getSelection();
		        
		        if (selection.size() > 0){
		        	
		        Scan selectedScan = ((Scan)selection.getFirstElement());
		       	
		        /// Using async approach so that message can be displayed in the tree while getting the scans list
				showMessage(String.format(RUNNING, selectedScan.getID()));

				toolBarActions.getScanResultsAction().setEnabled(false);
		
				CompletableFuture.runAsync(() -> {
					alreadyRunning = true;
					List<DisplayModel> scanResults = DataProvider.INSTANCE.getResultsForScanId(selectedScan.getID());
		
					rootModel.children.clear();
					rootModel.children.addAll(scanResults);
					viewer.getTree().getDisplay().asyncExec(() -> viewer.refresh());
					toolBarActions.getScanResultsAction().setEnabled(true);
					alreadyRunning = false;
					

				});
		        
		        //end
		        }
		    }
		});
		
		PluginUtils.setTextForComboViewer(scanIdComboViewer, SCAN_COMBO_VIEWER_TEXT);
		
		GridData gridData = new GridData();
		gridData.widthHint = 510;
		scanIdComboViewer.getCombo().setLayoutData(gridData);
		PluginUtils.enableComboViewer(scanIdComboViewer, false);
	}
	
	
	private void configureTreeItemSelectionChangeEvent(TreeViewer viewer) {

		viewer.addSelectionChangedListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				// if the selection is empty clear the label
				if (event.getSelection().isEmpty()) {
					return;
				}
				if (event.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection selection = (IStructuredSelection) event.getSelection();
					DisplayModel selectedItem = (DisplayModel) selection.getFirstElement();

					// added this line to generate the view dynamically
					// createDetailsSection(viewer);

					if (selectedItem.getType() != null) {
						typeValueText.setText((selectedItem.getType()).toUpperCase());
					}

					if (selectedItem.getSeverity() != null) {
						severityValueText.setText(selectedItem.getSeverity());
					}
					
					if(selectedItem.getResult()!= null)
					{
						if (selectedItem.getResult().getStatus() != null) {
							statusValueText.setText(selectedItem.getResult().getStatus());
						}

						if (selectedItem.getResult().getData().getDescription() != null) {
							descriptionValueText.setText(selectedItem.getResult().getData().getDescription());
						} else {
							descriptionValueText.setText("Not Available.");
						}
						
					}
									
					resultInfoCompositePanel.setVisible(true);
					if(selectedItem.getType()!=null)
					{
					updateAttackVectorForSelectedTreeItem(selectedItem);
					}


				}
			}
		});

	}



	private void updateAttackVectorForSelectedTreeItem(DisplayModel selectedItem) {

		clearAttackVectorSection(attackVectorCompositePanel);
		attackVectorCompositePanel.setVisible(true);
		
		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SCA_DEPENDENCY)) {
			
			List<PackageData> packageDataList = selectedItem.getResult().getData().getPackageData();

			if (packageDataList!= null && !packageDataList.isEmpty()) {
				
				for(PackageData packageDataItem : packageDataList)
				{
					Text packageDataTypeLabel = new Text(attackVectorCompositePanel, SWT.READ_ONLY);
					packageDataTypeLabel.setFont(boldFont);
					packageDataTypeLabel.setText(packageDataItem.getType());
					
					Link packageDataLink = new Link(attackVectorCompositePanel, SWT.NONE);
					packageDataLink.setText("<a>" + packageDataItem.getUrl() + "</a>");
					
					
				}
				
				attackVectorCompositePanel.layout();
								
			} else {
				if (attackVectorValueLinkText != null) {
					attackVectorValueLinkText.setText("Not Available.");
				}
			}

		}
		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.KICS_INFRASTRUCTURE)) {

		}
		
		
		if (selectedItem.getType().equalsIgnoreCase(PluginConstants.SAST)) {

			String queryName = selectedItem.getResult().getData().getQueryName();
			String groupName = selectedItem.getResult().getData().getGroup();
			
			List<Node> nodesList = selectedItem.getResult().getData().getNodes();
			if (nodesList != null && nodesList.size() > 0) {
				

				for (Node node : nodesList) {
					
					String nodeName = node.getName();
					String markerDescription = groupName+"_"+queryName+"_"+ nodeName;
					
					// attackVectorValueText = new Text(attackVectorCompositePanel, SWT.READ_ONLY);
					// attackVectorValueText.setText(node.getFileName() + "[" + node.getLine() + ","
					// + node.getColumn() + "]");

					Link attackVectorValueLinkText = new Link(attackVectorCompositePanel, SWT.NONE);
					String text = "<a>" + node.getFileName() + "[" + node.getLine() + "," + node.getColumn() + "]"
							+ "</a>";
					attackVectorValueLinkText.setText(text);
					attackVectorValueLinkText.addListener(SWT.Selection, new Listener() {
						public void handleEvent(Event event) {
							openTheSelectedFile(node.getFileName(), node.getLine(), markerDescription);
						}
					});

					attackVectorCompositePanel.layout();

				}
			} else {
				if (attackVectorValueLinkText != null) {
					attackVectorValueLinkText.setText("Not Available.");
				}

			}
		}

	}

	private void clearAttackVectorSection(Composite attackVectorCompositePanel) {
		
		for (Control child : attackVectorCompositePanel.getChildren()) {
			
			if(!(child instanceof Label))
			child.dispose();
		}
	}
	
	private void openTheSelectedFile(String fileName, Integer lineNumber, String markerDescription) {

		Path filePath = new Path(fileName);
		List<IFile> filesFound = findFileInWorkspace(filePath.lastSegment());

		for (IFile file : filesFound) {
			Path fullPath = (Path) file.getFullPath();
			Path absolutePathOfFoundFile = (Path) fullPath.removeFirstSegments(1).makeAbsolute();

			if (absolutePathOfFoundFile.equals(filePath)) {
				try {
					IMarker fileMarker = file.createMarker(IMarker.TEXT);
					fileMarker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					// IDE.openEditor(marker); //3.0 API
					IDE.openEditor(page, fileMarker);
					// marker.delete();
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}

		}

	}


	private List<IFile> findFileInWorkspace(final String fileName) {
		final List<IFile> foundFiles = new ArrayList<IFile>();
		try {
			// visiting only resources proxy because we obtain the resource only when
			// matching name, thus the workspace traversal is much faster
			ResourcesPlugin.getWorkspace().getRoot().accept(new IResourceProxyVisitor() {
				@Override
				public boolean visit(IResourceProxy resourceProxy) throws CoreException {
					if (resourceProxy.getType() == IResource.FILE) {
						String resourceName = resourceProxy.getName();
						if (resourceName.equals(fileName)) {
							IFile foundFile = (IFile) resourceProxy.requestResource();
							foundFiles.add(foundFile);
						}
					}
					return true;
				}
			}, IResource.NONE);
		} catch (Exception e) {
//			CxLogger.getLogger().error("Error occured while searching for file name in project",e);
			e.printStackTrace();
		}
		return foundFiles;
	}

	private void createColumns() {
		TreeViewerColumn col = createTreeViewerColumn("Title", 500);
		ColumnProvider label = new ColumnProvider(this::findSeverityImage, model -> model.name);
		col.setLabelProvider((label));

	}

	private TreeViewerColumn createTreeViewerColumn(String title, int bound) {
		final TreeViewerColumn viewerColumn = new TreeViewerColumn(viewer, SWT.NONE);
		final TreeColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(true);
		return viewerColumn;
	}

	private Image findSeverityImage(DisplayModel model) {
		String severity = model.severity;
		if (severity == null)
			return null;

		if (severity.equalsIgnoreCase("critical"))
			return CRITICAL_SEVERITY;
		if (severity.equalsIgnoreCase("high"))
			return HIGH_SEVERITY;
		if (severity.equalsIgnoreCase("medium"))
			return MEDIUM_SEVERITY;
		if (severity.equalsIgnoreCase("low"))
			return LOW_SEVERITY;
		if (severity.equalsIgnoreCase("info"))
			return INFO_SEVERITY;

		return null;
	}


	public void showMessage(String message) {
		rootModel.children.clear();
		rootModel.children.add(DataProvider.INSTANCE.message(message));
		viewer.refresh();
		// monitorActions.forEach(act -> act.setEnabled(true));
	}

	private void clearSelectionFromTheViewers() {
		resultInfoCompositePanel.setVisible(false);
		attackVectorCompositePanel.setVisible(false);
		

		
		clearResultsTreeViewer();
		leftCompositePanel.layout();
		
		clearScanIdComboViewer();
		clearProjectComboViewer();
		reloadProjectComboViewer();
		
		
	}
	
	private void clearResultsTreeViewer() {
		rootModel.children.clear();
		viewer.refresh();
	}
	
	private void clearScanIdComboViewer() {
		PluginUtils.enableComboViewer(scanIdComboViewer, false);
		scanIdComboViewer.refresh();
		scanIdComboViewer.setInput(Collections.EMPTY_LIST);
		PluginUtils.setTextForComboViewer(scanIdComboViewer, SCAN_COMBO_VIEWER_TEXT);
		scanIdComboViewer.getCombo().update();
		
		
	}
	
	private void clearProjectComboViewer() {
		projectComboViewer.setInput(Collections.EMPTY_LIST);
		PluginUtils.setTextForComboViewer(projectComboViewer, PROJECT_COMBO_VIEWER_TEXT);
	}
	
	
	private void reloadProjectComboViewer() {
		PluginUtils.enableComboViewer(projectComboViewer, false);
		PluginUtils.setTextForComboViewer(projectComboViewer, "Getting the projects from AST server...");
		projectComboViewer.getCombo().update();
		List<Project> projectList = DataProvider.INSTANCE.getProjectList();
		projectComboViewer.setInput(projectList);
		projectComboViewer.refresh();
		PluginUtils.setTextForComboViewer(projectComboViewer ,PROJECT_COMBO_VIEWER_TEXT);
		PluginUtils.enableComboViewer(projectComboViewer, true);
	
	}
}

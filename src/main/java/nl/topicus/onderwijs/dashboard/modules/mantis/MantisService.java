package nl.topicus.onderwijs.dashboard.modules.mantis;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import nl.topicus.onderwijs.dashboard.config.ISettings;
import nl.topicus.onderwijs.dashboard.datasources.Issues;
import nl.topicus.onderwijs.dashboard.datatypes.Issue;
import nl.topicus.onderwijs.dashboard.datatypes.IssueStatus;
import nl.topicus.onderwijs.dashboard.keys.Key;
import nl.topicus.onderwijs.dashboard.modules.AbstractService;
import nl.topicus.onderwijs.dashboard.modules.DashboardRepository;
import nl.topicus.onderwijs.dashboard.modules.ServiceConfiguration;
import nl.topicus.onderwijs.dashboard.modules.ns.NSService;

import org.mantisbt.connect.IMCSession;
import org.mantisbt.connect.MCException;
import org.mantisbt.connect.axis.MCSession;
import org.mantisbt.connect.model.IIssueHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@ServiceConfiguration(interval = 1, unit = TimeUnit.MINUTES)
public class MantisService extends AbstractService {
	private static final Logger log = LoggerFactory.getLogger(NSService.class);

	private Map<Key, List<Issue>> issues = new HashMap<Key, List<Issue>>();

	@Autowired
	public MantisService(ISettings settings) {
		super(settings);
	}

	@Override
	public void onConfigure(DashboardRepository repository) {
		for (Key key : getSettings().getKeysWithConfigurationFor(
				MantisService.class)) {
			issues.put(key, Collections.<Issue> emptyList());
			repository.addDataSource(key, Issues.class, new IssuesImpl(key,
					this));
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void refreshData() {
		Map<Key, List<Issue>> newIssues = new HashMap<Key, List<Issue>>();
		Map<Key, Map<String, ?>> serviceSettings = getSettings()
				.getServiceSettings(MantisService.class);
		for (Map.Entry<Key, Map<String, ?>> configEntry : serviceSettings
				.entrySet()) {
			Key project = configEntry.getKey();
			String url = configEntry.getValue().get("url").toString();
			String username = configEntry.getValue().get("username").toString();
			String password = configEntry.getValue().get("password").toString();
			List<Integer> projectIds = (List<Integer>) configEntry.getValue()
					.get("projects");
			int filter = (Integer) configEntry.getValue().get("filter");
			newIssues.put(project, fetchIssues(project, projectIds, filter,
					url, username, password));
		}
		issues = newIssues;
	}

	private List<Issue> fetchIssues(Key project, List<Integer> projectIds,
			int filter, String url, String username, String password) {
		try {
			List<Issue> issues = new ArrayList<Issue>();
			IMCSession session = new MCSession(new URL(url), username, password);
			for (int curProject : projectIds) {
				for (IIssueHeader curIssue : session.getIssueHeaders(
						curProject, filter, 10)) {
					if (IssueStatus.get(curIssue.getStatus()) == IssueStatus.NEW)
						issues.add(new Issue(project, curIssue));
				}
			}
			return issues;
		} catch (MalformedURLException e) {
			log.error("Unable to refresh data from mantis: {} {}", e.getClass()
					.getSimpleName(), e.getMessage());
		} catch (MCException e) {
			log.error("Unable to refresh data from mantis: {} {}", e.getClass()
					.getSimpleName(), e.getMessage());
		}
		return Collections.emptyList();
	}

	public List<Issue> getIssues(Key key) {
		return issues.get(key);
	}
}
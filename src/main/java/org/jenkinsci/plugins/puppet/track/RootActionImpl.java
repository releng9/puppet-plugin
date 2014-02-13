package org.jenkinsci.plugins.puppet.track;

import hudson.Extension;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.puppet.track.report.PuppetEvent;
import org.jenkinsci.plugins.puppet.track.report.PuppetReport;
import org.jenkinsci.plugins.puppet.track.report.PuppetStatus;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;

/**
 * Exposed at /puppet to receive report submissions from puppet.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class RootActionImpl implements RootAction {
    @Inject
    private Jenkins jenkins;

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "puppet";
    }

    /**
     * Receives the submission from HTTP reporter to track fingerprints.
     */
    @RequirePOST
    public HttpResponse doReport(StaplerRequest req) throws IOException {
        // TODO: permission check
        // TODO: stapler YAML support

        PuppetReport r = PuppetReport.load(req.getReader());

        String host = r.host;
        if (host==null)     host = "unknown";

        String env = r.environment;
        if (env==null)      env = "unknown";

        for (PuppetStatus st : r.resource_statuses.values()) {
            // TODO: pluggability for matching resources
            if (st.resource_type.equals("File")) {
                for (PuppetEvent ev : st.events) {
                    DeploymentFacet df = getDeploymentFacet(ev.getNewChecksum());
                    if (df!=null) {
                        df.add(new ServerDeploymentRecord(host, env, st.title));
                    }

                    // TODO: record undeploy
                }
            }
        }

        return HttpResponses.ok();
    }

    /**
     * Resolve {@link DeploymentFacet} to attach the record to, or null if there's none.
     */
    private DeploymentFacet getDeploymentFacet(String md5) throws IOException {
        if (md5==null)  return null;

        Fingerprint f = jenkins.getFingerprintMap().get(md5);
        if (f==null)    return null;

        Collection<FingerprintFacet> facets = f.getFacets();
        DeploymentFacet df = findDeploymentFacet(facets);
        if (df==null) {
            df = new DeploymentFacet(f,System.currentTimeMillis());
            facets.add(df);
        }
        return df;
    }

    private DeploymentFacet findDeploymentFacet(Collection<FingerprintFacet> facets) {
        for (DeploymentFacet df : Util.filter(facets, DeploymentFacet.class)) {
            return df;
        }
        return null;
    }
}

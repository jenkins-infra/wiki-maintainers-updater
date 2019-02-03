package io.jenkins.infra.wiki_maintainers

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import java.util.logging.Level
import java.util.logging.Logger

class WikiMaintainersUpdater {

    /**
     * URL to JSON with a list of valid Artifactory user names.
     */
    private static final String ARTIFACTORY_USER_NAMES_URL = System.getProperty('artifactoryUserNamesJsonListUrl')

    /**
    /**
     * URL to the REST API Extender API of Confluence
     */
    private static final String CONFLUENCE_API_URL = 'https://wiki.jenkins.io/rest/extender/1.0'

    /**
     * Prefix for permission target generated and maintained (i.e. possibly deleted) by this program.
     */
    private static final String CONFLUENCE_GROUP = 'auto-managed-maintainers'

    /**
     * If enabled, will not send PUT/DELETE requests to Confluence, only GET (i.e. not modifying).
     */
    private static final boolean DRY_RUN_MODE = Boolean.getBoolean('dryRun')

    static void main(String[] args) throws IOException {
        if (DRY_RUN_MODE) System.err.println("Running in dry run mode")

        def knownUsers = []
        if (ARTIFACTORY_USER_NAMES_URL) {
            knownUsers = new JsonSlurper().parse(new URL(ARTIFACTORY_USER_NAMES_URL))
        }

        JsonBuilder json = new JsonBuilder()
        json {
            users knownUsers//.subList(0, 49)
        }

        String pretty = json.toPrettyString()


        System.out.println(pretty)

        if (DRY_RUN_MODE) {
            return
        }

        try {
            // https://it-lab.atlassian.net/wiki/spaces/RAEC/pages/10059921/Manage+users+in+groups
            URL apiUrl = new URL(CONFLUENCE_API_URL + '/group/addUsers/' + CONFLUENCE_GROUP)
    
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection()
    
            conn.setRequestMethod('POST')
            conn.addRequestProperty('X-Atlassian-Token', 'nocheck')
            conn.addRequestProperty('Authorization', 'Basic ' + "${System.env.CONFLUENCE_USERNAME}:${System.env.CONFLUENCE_PASSWORD}".bytes.encodeBase64().toString())
            conn.addRequestProperty('Content-Type', 'application/json')
            conn.setDoOutput(true)
            OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream())
            osw.write(pretty)
            osw.close()

            LOGGER.info("Response code: " + conn.getResponseCode())
            if (conn.getResponseCode() < 200 || 299 <= conn.getResponseCode()) {
                // failure
                String error = conn.getInputStream()?.text
                LOGGER.log(Level.WARNING, "Failed to update group: ${error}")
            } else {
                String info = conn.getInputStream()?.text
                LOGGER.log(Level.INFO, "Successfully updated group: ${info}")
            }
        } catch (MalformedURLException mfue) {
            LOGGER.log(Level.WARNING, "Not a valid URL", mfue)
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Failed sending POST", ioe)
        }
    }

    private static final Logger LOGGER = Logger.getLogger(WikiMaintainersUpdater.class.name)
}

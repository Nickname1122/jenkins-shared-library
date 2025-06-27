def call(Map args = [:]) {
    def appId = args.appId ?: error("Missing required parameter: appId")
    def installationId = args.installationId ?: error("Missing required parameter: installationId")
    def credentialsId = args.credentialsId ?: error("Missing required parameter: credentialsId")

    return generateGithubAppToken(appId, installationId, credentialsId)
}

/**
 * Generates a GitHub App token using the provided App ID, installation ID, and credentials file.
 */
def generateGithubAppToken(String appId, String installationId, String credentialsId) {
    def token = ''

    withCredentials([file(credentialsId: credentialsId, variable: 'PRIVATE_KEY_PATH')]) {
        token = sh(
            script: buildJwtScript(appId, installationId),
            returnStdout: true
        ).trim()
    }

    return token
}

/**
 * Returns a shell script string that builds a JWT and retrieves a GitHub App access token.
 */
def buildJwtScript(String appId, String installationId) {
    return '#!/bin/bash\n' +
           'set -e\n\n' +

           'iat=$(date +%s)\n' +
           'exp=$((iat + 540)) # Token valid for 9 minutes\n\n' +

           'header=\'{"alg":"RS256","typ":"JWT"}\'\n' +
           'payload="{\\"iat\\":$iat,\\"exp\\":$exp,\\"iss\\":' + appId + '}"\n\n' +

           'b64enc() { openssl base64 -e -A | tr \'+/\' \'-_\' | tr -d \'=\'; }\n\n' +

           'header_b64=$(echo -n "$header" | b64enc)\n' +
           'payload_b64=$(echo -n "$payload" | b64enc)\n' +
           'data="$header_b64.$payload_b64"\n\n' +

           'signature=$(echo -n "$data" | openssl dgst -sha256 -sign "$PRIVATE_KEY_PATH" | b64enc)\n' +
           'jwt="$data.$signature"\n\n' +

           'curl -s -X POST \\\n' +
           '    -H "Authorization: Bearer $jwt" \\\n' +
           '    -H "Accept: application/vnd.github+json" \\\n' +
           '    https://api.github.com/app/installations/' + installationId + '/access_tokens \\\n' +
           '| jq -r .token\n'
}


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
    def appId = appId
    def installationId = installationId

    return """#!/bin/bash
        set -e

        iat=\$(date +%s)
        exp=\$((iat + 540)) # Token valid for 9 minutes

        header='{"alg":"RS256","typ":"JWT"}'
        payload="{\\"iat\\":\$iat,\\"exp\\":\$exp,\\"iss\\":${appId}}"

        b64enc() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }

        header_b64=\$(echo -n "\$header" | b64enc)
        payload_b64=\$(echo -n "\$payload" | b64enc)
        data="\$header_b64.\$payload_b64"

        signature=\$(echo -n "\$data" | openssl dgst -sha256 -sign "\$PRIVATE_KEY_PATH" | b64enc)
        jwt="\$data.\$signature"

        curl -s -X POST \\
            -H "Authorization: Bearer \$jwt" \\
            -H "Accept: application/vnd.github+json" \\
            https://api.github.com/app/installations/${installationId}/access_tokens \\
        | jq -r .token
    """
}

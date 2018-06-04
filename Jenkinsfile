#!groovy
timestamps {
    node {
        try {
            checkout scm
			sh 'git rev-parse HEAD > commit'
			def gitRevision = readFile('commit').trim()
			echo "Revision: ${gitRevision}" 
            def marketplaceVersion = "1.0.0.${env.BUILD_NUMBER}"

            // Run bash with -it to keep the container alive while we copy files in and run the build
            docker.image('frekele/ant:1.9.7-jdk8').withRun('-v /var/lib/ivy2/cache:/root/.ivy2/cache') {c ->
                sh """
				docker cp ./ ${c.id}:/root
                docker exec ${c.id} ant -f /root/sources/build.xml dist
                rm -rf ./dist
                docker cp ${c.id}:/root/dist ./
                """
            }
            buildImage("sla-assurance-manager:${marketplaceVersion}", "--build-arg GIT_REVISION=${gitRevision} sla-assurance-manager")
			
			currentBuild.result = 'SUCCESS'
        } catch (any) {
			currentBuild.result = 'FAILURE'
			throw any
		} finally {
			step([$class: 'Mailer', recipients: 'paolo.barone@hpe.com'])
		}
    }
}

def buildImage(String tag, String args = '.') {
    docker.withRegistry('https://5gex.tmit.bme.hu') {
        def image = docker.build(tag, args)
        image.push('unstable')
    }
}

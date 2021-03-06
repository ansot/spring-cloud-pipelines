package io.springframework.cloud

import groovy.io.FileType
import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.dsl.GeneratedItems
import javaposse.jobdsl.dsl.MemoryJobManagement
import javaposse.jobdsl.dsl.ScriptRequest
import spock.lang.Specification
import spock.lang.Unroll
/**
 * Tests that all dsl scripts in the jobs directory will compile.
 */
class JobScriptsSpec extends Specification {

	@Unroll
	def 'should compile script #file.name'() {
		given:

		MemoryJobManagement jm = new MemoryJobManagement()
		defaultStubbing(jm)
		jm.parameters << [
				SCRIPTS_DIR: 'foo',
				JENKINSFILE_DIR: 'foo'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(file.text)])

		then:
		noExceptionThrown()

		and:
		if (file.name.endsWith('jenkins_pipeline_sample.groovy')) {
			List<String> jobNames = scripts.jobs.collect { it.jobName }
			assert jobNames.find { it == "github-analytics-pipeline-build" }
			assert jobNames.find { it == "github-webhook-pipeline-build" }
			assert jobNames.find { it.contains("stage") }
			assert jobNames.find { it.contains("prod-env-deploy") }
		}

		where:
		file << jobFiles
	}

	private void defaultStubbing(MemoryJobManagement jm) {
		jm.availableFiles['foo/Jenkinsfile-sample'] = new File('declarative-pipeline/Jenkinsfile-sample').text
		jm.availableFiles['foo/pipeline.sh'] = JobScriptsSpec.getResource('/pipeline.sh').text
		jm.availableFiles['foo/build_and_upload.sh'] = JobScriptsSpec.getResource('/build_and_upload.sh').text
		jm.availableFiles['foo/build_api_compatibility_check.sh'] = JobScriptsSpec.getResource('/build_api_compatibility_check.sh').text
		jm.availableFiles['foo/test_deploy.sh'] = JobScriptsSpec.getResource('/test_deploy.sh').text
		jm.availableFiles['foo/test_smoke.sh'] = JobScriptsSpec.getResource('/test_smoke.sh').text
		jm.availableFiles['foo/test_rollback_deploy.sh'] = JobScriptsSpec.getResource('/test_rollback_deploy.sh').text
		jm.availableFiles['foo/test_rollback_smoke.sh'] = JobScriptsSpec.getResource('/test_rollback_smoke.sh').text
		jm.availableFiles['foo/stage_deploy.sh'] = JobScriptsSpec.getResource('/stage_deploy.sh').text
		jm.availableFiles['foo/stage_e2e.sh'] = JobScriptsSpec.getResource('/stage_e2e.sh').text
		jm.availableFiles['foo/prod_deploy.sh'] = JobScriptsSpec.getResource('/prod_deploy.sh').text
		jm.availableFiles['foo/prod_complete.sh'] = JobScriptsSpec.getResource('/prod_complete.sh').text
	}

	def 'should compile seed job'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("seed/jenkins_pipeline.groovy").text)])

		then:
		noExceptionThrown()

		and:
		scripts.jobs.collect { it.jobName }.containsAll(["jenkins-pipeline-cf-seed", "jenkins-pipeline-k8s-seed"])
	}

	def 'should not include stage jobs when that option was unchecked'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		defaultStubbing(jm)
		jm.parameters << [
			SCRIPTS_DIR: 'foo',
			JENKINSFILE_DIR: 'foo',
			DEPLOY_TO_STAGE_STEP_REQUIRED: 'false'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("jobs/jenkins_pipeline_sample.groovy").text)])

		then:
		noExceptionThrown()

		and:
		scripts.jobs.every { !it.jobName.contains("stage") }
		jm.savedConfigs.find { it.key == "github-webhook-pipeline-test-env-rollback-test" }.with {
			assert it.value.contains("<downstreamProjectNames>github-webhook-pipeline-prod-env-deploy</downstreamProjectNames>")
			return it
		}
	}

	def 'should automatically run api compatibility by default'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		jm.parameters << [
			SCRIPTS_DIR: 'foo',
			JENKINSFILE_DIR: 'foo'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("jobs/jenkins_pipeline_sample.groovy").text)])

		then:
		noExceptionThrown()

		and:
		jm.savedConfigs.find { it.key == "github-webhook-pipeline-build" }.with {
			assert it.value.contains("hudson.plugins.parameterizedtrigger.BuildTrigger")
			assert it.value.contains("<projects>github-webhook-pipeline-build-api-check</projects>")
			assert !it.value.contains("au.com.centrumsystems.hudson.plugin.buildpipeline.trigger.BuildPipelineTrigger")
			return it
		}
	}

	def 'should not run api compatibility if that option is checked'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		jm.parameters << [
			SCRIPTS_DIR: 'foo',
			JENKINSFILE_DIR: 'foo',
			API_COMPATIBILITY_STEP_REQUIRED: 'false'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("jobs/jenkins_pipeline_sample.groovy").text)])

		then:
		noExceptionThrown()

		and:
		jm.savedConfigs.find { it.key == "github-webhook-pipeline-build" }.with {
			assert it.value.contains("hudson.plugins.parameterizedtrigger.BuildTrigger")
			assert it.value.contains("<projects>github-webhook-pipeline-test-env-deploy</projects>")
			assert !it.value.contains("<projects>github-webhook-pipeline-build-api-check</projects>")
			assert !it.value.contains("au.com.centrumsystems.hudson.plugin.buildpipeline.trigger.BuildPipelineTrigger")
			return it
		}
		!scripts.jobs.find { it.jobName == "github-webhook-pipeline-build-api-check" }
	}

	def 'should automatically deploy to stage if that option is checked'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		jm.parameters << [
			SCRIPTS_DIR: 'foo',
			JENKINSFILE_DIR: 'foo',
			AUTO_DEPLOY_TO_STAGE: 'true'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("jobs/jenkins_pipeline_sample.groovy").text)])

		then:
		noExceptionThrown()

		and:
		jm.savedConfigs.find { it.key == "github-webhook-pipeline-test-env-rollback-test" }.with {
			assert it.value.contains("hudson.plugins.parameterizedtrigger.BuildTrigger")
			assert it.value.contains("<projects>github-webhook-pipeline-stage-env-deploy</projects>")
			assert !it.value.contains("au.com.centrumsystems.hudson.plugin.buildpipeline.trigger.BuildPipelineTrigger")
			return it
		}
	}

	def 'should manually deploy to stage by default'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		jm.parameters << [
			SCRIPTS_DIR: 'foo',
			JENKINSFILE_DIR: 'foo'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("jobs/jenkins_pipeline_sample.groovy").text)])

		then:
		noExceptionThrown()

		and:
		jm.savedConfigs.find { it.key == "github-webhook-pipeline-test-env-rollback-test" }.with {
			assert !it.value.contains("hudson.plugins.parameterizedtrigger.BuildTrigger")
			assert !it.value.contains("<projects>github-webhook-pipeline-stage-env-deploy</projects>")
			assert it.value.contains("au.com.centrumsystems.hudson.plugin.buildpipeline.trigger.BuildPipelineTrigger")
			assert it.value.contains("<downstreamProjectNames>github-webhook-pipeline-stage-env-deploy</downstreamProjectNames>")
			return it
		}
	}

	def 'should automatically deploy to prod if that option is checked'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		jm.parameters << [
			SCRIPTS_DIR: 'foo',
			JENKINSFILE_DIR: 'foo',
			AUTO_DEPLOY_TO_PROD: 'true'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("jobs/jenkins_pipeline_sample.groovy").text)])

		then:
		noExceptionThrown()

		and:
		jm.savedConfigs.find { it.key == "github-webhook-pipeline-stage-env-test" }.with {
			assert it.value.contains("hudson.plugins.parameterizedtrigger.BuildTrigger")
			assert it.value.contains("<projects>github-webhook-pipeline-prod-env-deploy</projects>")
			assert !it.value.contains("au.com.centrumsystems.hudson.plugin.buildpipeline.trigger.BuildPipelineTrigger")
			return it
		}
	}

	def 'should manually deploy to prod by default'() {
		given:
		MemoryJobManagement jm = new MemoryJobManagement()
		jm.parameters << [
			SCRIPTS_DIR: 'foo',
			JENKINSFILE_DIR: 'foo'
		]
		DslScriptLoader loader = new DslScriptLoader(jm)

		when:
		GeneratedItems scripts = loader.runScripts([new ScriptRequest(
				new File("jobs/jenkins_pipeline_sample.groovy").text)])

		then:
		noExceptionThrown()

		and:
		jm.savedConfigs.find { it.key == "github-webhook-pipeline-stage-env-test" }.with {
			assert !it.value.contains("hudson.plugins.parameterizedtrigger.BuildTrigger")
			assert !it.value.contains("<projects>github-webhook-pipeline-prod-env-deploy</projects>")
			assert it.value.contains("au.com.centrumsystems.hudson.plugin.buildpipeline.trigger.BuildPipelineTrigger")
			assert it.value.contains("<downstreamProjectNames>github-webhook-pipeline-prod-env-deploy</downstreamProjectNames>")
			return it
		}
	}

	static List<File> getJobFiles() {
		List<File> files = []
		new File('jobs').eachFileRecurse(FileType.FILES) {
			files << it
		}
		return files
	}

}


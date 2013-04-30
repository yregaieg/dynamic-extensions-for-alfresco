package nl.runnable.alfresco.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.BuildException


/**
 * Gradle plugin that configures build settings for an Alfresco Dynamic Extension.
 * 
 * @author Laurens Fridael
 *
 */
class DynamicExtensionPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		configurePlugins(project)
		configureExtensions(project)
		configureInstallBundleTask(project)
		project.afterEvaluate {
			configureDependencies(project)
			configureRepositories(project)
			configureJarManifest(project)
		}
	}

	void configurePlugins(Project project) {
		project.apply plugin: 'java'
		project.apply plugin: 'osgi'
	}

	void configureExtensions(Project project) {
		project.convention.plugins[ProjectConvention.class.name] = new ProjectConvention(project)
		project.extensions.create('bundle', BundleExtension)
	}

	void configureInstallBundleTask(Project project) {
		def task = project.tasks.add('installBundle')
		task.dependsOn('build')
		task.doFirst {
			if (!installDirectory) {
				throw new BuildException("Bundle install directory not specified.", null)
			}
			File dir = new java.io.File(installDirectory)
			if (!dir.exists()) {
				throw new BuildException("Directory '$directory' does not exist.", null)
				logger.error()
			} else if (!dir.isDirectory()) {
				throw new BuildException("'$directory' is not a directory", null)
			}			
		}
		task << {
			project.copy {
				from project.jar.archivePath
				into installDirectory
			}
		}
	}

	void configureDependencies(Project project) {
		def alfresco = [
			group: project.alfresco.group ?: 'org.alfresco',
			version: project.alfresco.version ?: Versions.ALFRESCO
		]
		def surf = [
			group: project.surf.group ?: 'org.springframework.extensions.surf',
			version: project.surf.version ?: Versions.SURF
		]
		def dynamicExtensions = [
			group: project.dynamicExtensions.group ?: 'nl.runnable.alfresco.dynamicextensions',
			version: project.dynamicExtensions.version ?: Versions.DYNAMIC_EXTENSIONS
		]
		project.dependencies {
			compile group: alfresco.group, name: 'alfresco-core', version: alfresco.version
			compile group: alfresco.group, name: 'alfresco-repository', version: alfresco.version
			compile group: alfresco.group, name: 'alfresco-data-model', version: alfresco.version
			compile group: surf.group, name: 'spring-webscripts', version: surf.version
			compile group: dynamicExtensions.group, name: 'annotations', version: dynamicExtensions.version
		}

		if (project.useJavaxAnnotations) {
			project.dependencies {
				compile group:'javax.inject', name: 'javax.inject', version: '1'
				compile group: 'org.apache.geronimo.specs', name: 'geronimo-annotation_1.1_spec', version: '1.0.1'
			}
		}
	}

	void configureJarManifest(Project project) {
		/* 
		 * These packages must be imported for code that uses CGLIB or Spring AOP. For the sake of convenience, this
		 * plugin preemptively adds these imports.  
		 * 
		 * Without these imports, you will get ClassNotFoundExceptions when using CGLIB proxies (generated by Spring)
		 * for classes that are loaded within the OSGi container. Dynamic Extensions Milestone 5 expands on the use of
		 * AOP. 
		 * 
		 * BND will not be able to detect the use of CGLIB and Spring AOP classes at build-time, hence these packages 
		 * must be specified manually.
		 */
		def additionalPackages = [
			'net.sf.cglib.core',
			'net.sf.cglib.proxy',
			'net.sf.cglib.reflect',
			'org.aopalliance.aop',
			'org.aopalliance.intercept',
			'org.springframework.aop',
			'org.springframework.aop.framework'
		]
		project.jar {
			manifest {
				instructionReplace 'Bundle-SymbolicName', (project.bundle.symbolicName ?: project.name)
				instructionReplace 'Bundle-Name', (project.bundle.name ?: project.name)
				instructionReplace 'Bundle-Description', (project.bundle.description ?: project.description)
				instruction 'Alfresco-Dynamic-Extension', 'true'
				instruction 'Import-Package', '*,' + additionalPackages.join(',')
			}
		}
	}

	void configureRepositories(Project project) {
		project.repositories {
			mavenCentral()
			maven { url 'https://artifacts.alfresco.com/nexus/content/groups/public' }
			maven { url 'http://repo.springsource.org/release' }
			maven { url 'https://raw.github.com/lfridael/dynamic-extensions-for-alfresco/mvn-repo/' }
		}
	}
	
}

class ProjectConvention {

	Project project
	def alfresco = [:]
	def surf = [:]
	def dynamicExtensions = [:]
	boolean useJavaxAnnotations = true

	ProjectConvention(Project project) {
		this.project = project
	}

	void useAlfrescoVersion(String version) {
		project.alfresco.version = version
	}

	void useDynamicExtensionsVersion(String version) {
		project.dynamicExtensions.version = version
	}

	void useSurfVersion(String version) {
		project.surf.version = version
	}

	void useJavaxAnnotations(boolean useJavaxAnnotations = true) {
		project.useJavaxAnnotations = useJavaxAnnotations
	}

	void toDirectory(String directory) {
		project.tasks['installBundle'].ext.installDirectory = directory;
	}

}

class BundleExtension {

	String symbolicName
	String name
	String description

}

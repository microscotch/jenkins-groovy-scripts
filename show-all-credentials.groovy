import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import groovy.json.*

  Jenkins.instance.getDescriptorList(com.cloudbees.plugins.credentials.Credentials.class).each {
    println it.displayName
  }
  
// When called without paramters will returns all global credentials
retrieveCredentials(folders:[],system:false)
// To retrieve specific credentials, just pass their ids as parameter.
// For instance to retrieve credentials credId_1 and credId_2, just perform
// such call:
// retrieveCredentials('credId_1','credId_2')
//retrieveCredentials(folders: ['Azur'])
//retrieveCredentials()
  
//def retrieveCredentials(String... credIds) {
def retrieveCredentials(Map args=[:]) {
  
  println args?.system
  println args?.folders
  
  def processProvider = { provider,folder=null ->
    def result=[]
    provider?.domainCredentials?.each { domainCredentials ->
      provider.getCredentials(domainCredentials.domain).findAll{ credential ->
        ( args?.credentials == null || args?.credentials.size() == 0 || credential.id in args?.credentials ) && 
        ( args?.scopes == null || args?.scopes.size() == 0 || credential.scope.toString() in args?.scopes )
      }.each { credential ->
        result+=displayCredential(folder,domainCredentials.domain.name,credential)
      }
    }
    result
  }
  
  def result=[]
  if(args?.system==false?: (args?.folders?false:true)==true) {
  	def credentialsProvider = Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider').first()
  	result+=processProvider(credentialsProvider)
  }

  Jenkins.instance.allItems.findAll{ item ->
    item.class.name == 'com.cloudbees.hudson.plugins.folder.Folder' &&
    ( args?.folders == null || item.name in args?.folders )
  }.each { folder ->
    
    credentialsProvider = folder.properties.find { property ->
      property.class.name == 'com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider$FolderCredentialsProperty'
    }
    result+=processProvider(credentialsProvider,folder.fullName)
  }
  
  println JsonOutput.prettyPrint(JsonOutput.toJson(result))
  // Prevent any returns on console script
  null
  
}

def displayCredential(def folderName, def domainName, def cred) {
  // Closure in charge to display credentials details
  def result=[:]
  result.class=cred.class.name
  result.displayName = cred.descriptor.displayName
  if(folderName) {
  	result.folder = folderName
  }
  result.domain = (domainName?:'Global')
  result.scope = (cred.scope as String)
  result.id = cred.id
  result.description = cred.description?:''
  
  def getRow = { data ->
    result+=data?:[:]
  }
  
  cred.with {
    switch(it.class.name) {
      case "com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl":
      result = getRow(username: username, password: password?.plainText?:'')
      break
        case "com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey":
      result = getRow(privateKey: privateKeySource?.privateKey?.plainText, passphrase: passphrase?.decrypt()?:'')
      break
        case "com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl":
      result = getRow(accessKey: accessKey?:'', secretKey: secretKey?.plainText?:'')
      break
        case "org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl":
      result = getRow(secret: secret?.plainText?:'')
      break
        case "org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl":
      result = getRow(content: content?.text?:'')
      break
        case "com.microsoft.azure.util.AzureCredentials":
      result = getRow(subscriptionId: subscriptionId?:'', clientId: clientId?:'', clientSecret: hudson.util.Secret.decrypt(clientSecret))
      break
        case "org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials":      
      result = getRow(clientCertificate: clientCertificate?:'', clientKey: clientKey?:'')
      break
        case "com.dabsquared.gitlabjenkins.connection.GitLabApiTokenImpl":
      result = getRow(apiToken: apiToken?.plainText?:'')
      break      
    }         
  }
  result?:[:]
}

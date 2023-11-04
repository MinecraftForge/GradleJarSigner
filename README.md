# GradleJarSigner
This is a simple gradle plugin that uses ant to execute the [jarsigner](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/jarsigner.html) utility. This embeds a signature in the jar file that can be used to verify its contents haven't been modified and came from a specific source. This does NOT create an external GPG signature file. The built in signing plugin does that.

I made this because I got tired of having to configure everything manually for this in every project, and I wanted to have a simple way of signing data in Github Actions.

### Usage
I haven't published this to the gradle plugin portal yet so until I do you need to have this in your settings.gradle.

    pluginManagement {
        repositories {
            gradlePluginPortal()
            maven { url = 'https://maven.minecraftforge.net/' }
        }
    }
And in your build.gradle

    plugins {
        id 'net.minecraftforge.gradlejarsigner'
    }
This will add a extension name 'jarSigner' to your project where you can configure the signing information, or you can configure it in each signing task.
    
    jarSigner {
        alias = 'key_name'
        storePass = 'store_password'
        keyPass = 'key_password'
        keyStoreFile = file('keystore_file')
        // Or you can specify the keystore file as a base64 encoded string.
        // This is mainly meant to allow it to be passed in via a Github Action Secret
        keyStoreData = 'aGVsbG8='
    }

Then to sign the `jar` task you can do `jarSigner.sign(jar)`, this works for any Jar or Zip task.
You can also configure the task itself to specify any of the information set in the global config as well as any filters on the data you wish to sign.

    jarSigner.sign(jar) {
        alias = 'key_name'
        storePass = 'store_password'
        keyPass = 'key_password'
        keyStoreFile = file('keystore_file')
        exclude 'unsigned.txt'
    }

### Github Secrets
A large motivation for this was wanting to use Github Actions and still be able to sign my built files. Github does not allow you to have files as [secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets) just strings and the workarounds I found involved committing a encrypted form of your keystore to your repo and then decrypting it during an Action. Instead I decided to allow you to specify the keystore file as a base64 encoded string which can be used as a Secret.

You can either manually configure the information by pulling the secrets yourself, or I added a simple helper `jarSigner.autoDetect()` which which search the following locations in order:

    if (prefix != null) {
        project.findProperty(prefix + '.' + prop)
        System.getenv(prefix + '.' + prop)
    }
    project.findProperty(prop)
    System.getenv(prop)
`prefix` defaults to `project.name` you can override by calling `jarSigner.autoDetect('prefix')`

For the following properties:

    jarSigner {
        alias = 'SIGN_KEY_ALIAS'
        keyPass = 'SIGN_KEY_PASSWORD'
        storePass = 'SIGN_KEYSTORE_PASSWORD'
        keyStoreData = 'SIGN_KEYSTORE_DATA'
    }

### Conclusion
I'm sure there are improvements that could be made, but it works good enough for me so this is where I'm at. If you have suggestions for improvements feel free to submit them. But the point of this plugin is to be small, simple, and single purpose.

grails.config.locations = []

grails.mime.disable.accept.header.userAgents = ['Gecko', 'WebKit', 'Presto', 'Trident']
grails.mime.types = [
    all          : '*/*',
    atom         : 'application/atom+xml',
    css          : 'text/css',
    csv          : 'text/csv',
    form         : 'application/x-www-form-urlencoded',
    html         : ['text/html', 'application/xhtml+xml'],
    js           : 'text/javascript',
    json         : ['application/json', 'text/json'],
    multipartForm: 'multipart/form-data',
    rss          : 'application/rss+xml',
    text         : 'text/plain',
    hal          : ['application/hal+json', 'application/hal+xml'],
    xml          : ['text/xml', 'application/xml']
]

grails.urlmapping.cache.maxsize = 1000

// Parse an incoming JSON body into request.JSON for the REST controllers.
grails.mime.file.extensions = false
grails.mime.use.accept.header = true

grails.converters.encoding = "UTF-8"
grails.converters.json.default.deep = false
grails.converters.domain.include.version = false

grails.enable.native2ascii = true
grails.views.gsp.encoding = "UTF-8"
grails.views.default.codec = "html"
grails.views.gsp.codecs.expression = "html"
grails.views.gsp.codecs.scriptlet = "html"
grails.views.gsp.codecs.taglib = "none"
grails.views.gsp.codecs.staticparts = "none"

grails.scaffolding.templates.domainSuffix = 'Instance'

grails.hibernate.cache.queries = false
grails.hibernate.osiv.readonly = false

// GORM: fail loudly on a save() of an invalid object (was grails.gorm.failOnError)
grails.gorm.failOnError = true

environments {
    development {
        grails.logging.jul.usebridge = true
    }
    production {
        grails.logging.jul.usebridge = false
    }
}

log4j.main = {
    error 'org.codehaus.groovy.grails.web.servlet',
          'org.codehaus.groovy.grails.web.pages',
          'org.codehaus.groovy.grails.web.sitemesh',
          'org.codehaus.groovy.grails.web.mapping.filter',
          'org.codehaus.groovy.grails.web.mapping',
          'org.codehaus.groovy.grails.commons',
          'org.codehaus.groovy.grails.plugins',
          'org.codehaus.groovy.grails.orm.hibernate',
          'org.springframework',
          'org.hibernate',
          'net.sf.ehcache.hibernate'

    warn 'org.mortbay.log'

    debug 'grails.app'
}

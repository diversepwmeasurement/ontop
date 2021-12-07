package it.unibz.inf.ontop.cli;

import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.help.BashCompletion;
import com.github.rvesse.airline.help.cli.bash.CompletionBehaviour;

abstract class AbstractOntopCommand implements OntopCommand {

    @Option(type = OptionType.COMMAND, name = {"-p", "--properties"}, title = "properties file",
            description = "Properties file")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String propertiesFile;

    @Option(type = OptionType.COMMAND, name = {"-d", "--db-metadata"}, title = "db-metadata file",
            description = "User-supplied db-metadata file")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String dbMetadataFile;

    @Option(type = OptionType.COMMAND, name = {"-v", "--ontop-views"}, title = "Ontop view file",
            description = "User-supplied view file")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String ontopViewFile;

    @Option(type = OptionType.COMMAND, name = {"--db-password"}, title = "DB password",
            description = "DB password (overrides the properties)")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String dbPassword;

    @Option(type = OptionType.COMMAND, name = {"-u", "--db-user"}, title = "DB user",
            description = "DB user (overrides the properties)")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String dbUser;

    @Option(type = OptionType.COMMAND, name = {"--db-url"}, title = "DB URL",
            description = "DB URL (overrides the properties)")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String dbUrl;

    @Option(type = OptionType.COMMAND, name = {"--db-name"}, title = "DB name",
            description = "DB name (overrides the properties)")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String dbName;

    @Option(type = OptionType.COMMAND, name = {"--db-driver"}, title = "DB driver",
            description = "DB driver (overrides the properties)")
    @BashCompletion(behaviour = CompletionBehaviour.FILENAMES)
    String dbDriver;
}

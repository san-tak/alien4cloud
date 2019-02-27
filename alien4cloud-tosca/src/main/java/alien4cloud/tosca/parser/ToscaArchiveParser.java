package alien4cloud.tosca.parser;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Resource;

import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.CsarDependenciesBean;
import org.springframework.stereotype.Component;

import alien4cloud.tosca.context.ToscaContextual;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.model.ToscaMeta;
import alien4cloud.tosca.parser.impl.ErrorCode;
import alien4cloud.tosca.parser.mapping.ToscaMetaMapping;
import alien4cloud.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Parse a TOSCA archive.
 */
@Slf4j
@Component
public class ToscaArchiveParser {
    public static final String TOSCA_META_FOLDER_NAME = "TOSCA-Metadata";
    public static final String TOSCA_META_FILE_LOCATION = FileSystems.getDefault().getSeparator() + TOSCA_META_FOLDER_NAME
            + FileSystems.getDefault().getSeparator() + "TOSCA.meta";

    @Resource
    private ToscaMetaMapping toscaMetaMapping;
    @Resource
    private ToscaParser toscaParser;
    @Resource
    private ToscaImportParser toscaImportParser;

    /**
     * Parse a TOSCA archive and reuse an existing TOSCA Context. Other methods will create an independent context for the parsing.
     * Note that a TOSCA Context MUST have been initialized in order to use this method.
     *
     * @param archiveFile The archive file to parse.
     * @return A parsing result that contains the Archive Root and eventual errors and/or warnings.
     * @throws ParsingException In case of a severe issue while parsing (incorrect yaml, no tosca file etc.)
     */
    public ParsingResult<ArchiveRoot> parseWithExistingContext(Path archiveFile) throws ParsingException {
        return parse(archiveFile, false);
    }

    /**
     * Parse a TOSCA archive with a fresh new TOSCA Context.
     *
     * @param archiveFile The archive file to parse.
     * @return A parsing result that contains the Archive Root and eventual errors and/or warnings.
     * @throws ParsingException In case of a severe issue while parsing (incorrect yaml, no tosca file etc.)
     */

    @ToscaContextual(requiresNew = true)
    public ParsingResult<ArchiveRoot> parse(Path archiveFile) throws ParsingException {
        return parse(archiveFile, false);
    }

    /**
     * Parse an archive file from a zip.
     *
     * @param archiveFile The archive file currently zipped.
     * @return A parsing result that contains the Archive Root and eventual errors and/or warnings.
     * @throws ParsingException In case of a severe issue while parsing (incorrect yaml, no tosca file etc.)
     */
    @ToscaContextual(requiresNew = true)
    public ParsingResult<ArchiveRoot> parse(Path archiveFile, boolean allowYamlFile) throws ParsingException {
        try (FileSystem csarFS = FileSystems.newFileSystem(archiveFile, null)) {
            if (Files.exists(csarFS.getPath(TOSCA_META_FILE_LOCATION))) {
                return parseFromToscaMeta(csarFS);
            }
            return parseFromRootDefinitions(csarFS, toscaParser);
        } catch (IOException e) {
            log.error("Unable to read uploaded archive [" + archiveFile + "]", e);
            throw new ParsingException("Archive",
                    new ParsingError(ErrorCode.FAILED_TO_READ_FILE, "Problem happened while accessing file", null, null, null, archiveFile.toString()));
        } catch (ProviderNotFoundException e) {
            if (allowYamlFile) {
                // the file may be a yaml so let's parse
                log.debug("File is not a tosca archive, try to parse it as tosca template. ", e);
                return toscaParser.parseFile(archiveFile);
            } else {
                log.warn("Failed to import archive", e);
                throw new ParsingException("Archive", new ParsingError(ErrorCode.ERRONEOUS_ARCHIVE_FILE,
                        "File is not in good format, only zip file is supported ", null, e.getMessage(), null, null));
            }
        }
    }

    // TODO Find a proper way to refactor avoid code duplication with parsing methods from file system

    /**
     * Parse an archive from a directory, it's very convenient for internal use and test
     *
     * @param archiveDir the directory which contains the archive
     * @return A parsing result that contains the Archive Root and eventual errors and/or warnings.
     * @throws ParsingException In case of a severe issue while parsing (incorrect yaml, no tosca file etc.)
     */
    @ToscaContextual(requiresNew = true)
    public ParsingResult<ArchiveRoot> parseDir(Path archiveDir) throws ParsingException {
        Path toscaMetaFile = archiveDir.resolve(TOSCA_META_FILE_LOCATION);
        if (Files.exists(toscaMetaFile)) {
            return parseFromToscaMeta(archiveDir);
        }
        return parseFromRootDefinitions(archiveDir);
    }

    @ToscaContextual(requiresNew = true)
    public ParsingResult<CsarDependenciesBean> parseImports(Path archiveFile) throws ParsingException {
        try (FileSystem csarFS = FileSystems.newFileSystem(archiveFile, null)) {
            if (Files.exists(csarFS.getPath(TOSCA_META_FILE_LOCATION))) {
                YamlSimpleParser<ToscaMeta> parser = new YamlSimpleParser<ToscaMeta>(toscaMetaMapping.getParser());
                ParsingResult<ToscaMeta> parsingResult = parser.parseFile(csarFS.getPath(TOSCA_META_FILE_LOCATION));
                CsarDependenciesBean csarDependenciesBean = initDependencyBeanFromToscaMeta(parsingResult.getResult());
                return parseFromToscaMeta(csarFS, parsingResult.getResult(), TOSCA_META_FILE_LOCATION, csarDependenciesBean, toscaImportParser);
            }
            return parseFromRootDefinitions(csarFS, toscaImportParser);
        } catch (IOException e) {
            log.error("Unable to read uploaded archive [" + archiveFile + "]", e);
            throw new ParsingException("Archive",
                    new ParsingError(ErrorCode.FAILED_TO_READ_FILE, "Problem happened while accessing file", null, null, null, archiveFile.toString()));
        } catch (ProviderNotFoundException e) {

            log.warn("Failed to import archive", e);
            throw new ParsingException("Archive", new ParsingError(ErrorCode.ERRONEOUS_ARCHIVE_FILE, "File is not in good format, only zip file is supported ",
                    null, e.getMessage(), null, null));
        }
    }

    private CsarDependenciesBean initDependencyBeanFromToscaMeta(ToscaMeta toscaMeta) {
        CsarDependenciesBean csarDependenciesBean = new CsarDependenciesBean();
        csarDependenciesBean.setSelf(new CSARDependency(toscaMeta.getName(), toscaMeta.getVersion()));
        return csarDependenciesBean;
    }

    private ParsingResult<ArchiveRoot> parseFromToscaMeta(Path csarPath) throws ParsingException {
        YamlSimpleParser<ToscaMeta> parser = new YamlSimpleParser<ToscaMeta>(toscaMetaMapping.getParser());
        ParsingResult<ToscaMeta> parsingResult = parser.parseFile(csarPath.resolve(TOSCA_META_FILE_LOCATION));
        ArchiveRoot archiveRoot = initFromToscaMeta(parsingResult);
        return parseFromToscaMeta(csarPath, parsingResult.getResult(), TOSCA_META_FILE_LOCATION, archiveRoot);
    }

    private ParsingResult<ArchiveRoot> parseFromToscaMeta(FileSystem csarFS) throws ParsingException {
        YamlSimpleParser<ToscaMeta> parser = new YamlSimpleParser<ToscaMeta>(toscaMetaMapping.getParser());
        ParsingResult<ToscaMeta> parsingResult = parser.parseFile(csarFS.getPath(TOSCA_META_FILE_LOCATION));
        // FIXME shouldn't we check here if the meta parsing went well?
        ArchiveRoot archiveRoot = initFromToscaMeta(parsingResult);
        return parseFromToscaMeta(csarFS, parsingResult.getResult(), TOSCA_META_FILE_LOCATION, archiveRoot, toscaParser);
    }

    private ArchiveRoot initFromToscaMeta(ParsingResult<ToscaMeta> toscaMeta) {
        ArchiveRoot archiveRoot = new ArchiveRoot();
        archiveRoot.getArchive().setName(toscaMeta.getResult().getName());
        if (toscaMeta.getResult().getVersion() != null) {
            archiveRoot.getArchive().setVersion(toscaMeta.getResult().getVersion());
        }
        if (toscaMeta.getResult().getCreatedBy() != null) {
            archiveRoot.getArchive().setTemplateAuthor(toscaMeta.getResult().getCreatedBy());
        }
        return archiveRoot;
    }

    private ParsingResult<ArchiveRoot> parseFromToscaMeta(Path csarPath, ToscaMeta toscaMeta, String metaFileName, ArchiveRoot instance)
            throws ParsingException {
        if (toscaMeta.getEntryDefinitions() != null) {
            return toscaParser.parseFile(csarPath.resolve(toscaMeta.getEntryDefinitions()), instance);
        }
        throw new ParsingException(metaFileName,
                new ParsingError(ErrorCode.ENTRY_DEFINITION_NOT_FOUND, "No entry definitions found in the meta file.", null, null, null, null));
    }

    private <T> ParsingResult<T> parseFromToscaMeta(FileSystem csarFS, ToscaMeta toscaMeta, String metaFileName, T instance, YamlParser<T> parser)
            throws ParsingException {
        if (toscaMeta.getEntryDefinitions() != null) {
            return parser.parseFile(csarFS.getPath(toscaMeta.getEntryDefinitions()), instance);
        }
        throw new ParsingException(metaFileName,
                new ParsingError(ErrorCode.ENTRY_DEFINITION_NOT_FOUND, "No entry definitions found in the meta file.", null, null, null, null));
    }

    private ParsingResult<ArchiveRoot> parseFromRootDefinitions(Path csarPath) throws ParsingException {
        // load definitions from the archive root
        try {
            List<Path> yamls = FileUtil.listFiles(csarPath, ".+\\.ya?ml");
            if (yamls.size() == 1) {
                return toscaParser.parseFile(yamls.get(0));
            }
            throw new ParsingException("Archive", new ParsingError(ErrorCode.SINGLE_DEFINITION_SUPPORTED,
                    "Alien only supports archives with a single root definition.", null, null, null, String.valueOf(yamls.size())));
        } catch (IOException e) {
            throw new ParsingException("Archive", new ParsingError(ErrorCode.FAILED_TO_READ_FILE, "Failed to list root definitions", null, null, null, null));
        }
    }

    private <T> ParsingResult<T> parseFromRootDefinitions(FileSystem csarFS, YamlParser<T> parser) throws ParsingException {
        // load definitions from the archive root
        try {
            DefinitionVisitor visitor = new DefinitionVisitor(csarFS);
            Files.walkFileTree(csarFS.getPath(csarFS.getSeparator()), EnumSet.noneOf(FileVisitOption.class), 1, visitor);
            if (visitor.getDefinitionFiles().size() == 1) {
                return parser.parseFile(visitor.getDefinitionFiles().get(0));
            }
            throw new ParsingException("Archive",
                    new ParsingError(ErrorCode.SINGLE_DEFINITION_SUPPORTED, "Alien only supports archives with a single root definition.", null, null, null,
                            "Matching file count in root of " + csarFS + ": " + visitor.getDefinitionFiles().size()));
        } catch (IOException e) {
            throw new ParsingException("Archive",
                    new ParsingError(ErrorCode.FAILED_TO_READ_FILE, "Failed to list root definitions", null, null, null, "Error reading " + csarFS + ": " + e));
        }
    }
}
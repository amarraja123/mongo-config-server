package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

import static com.example.MongoEnvironmentRepositoryConfiguration.MAP_KEY_DOT_REPLACEMENT;

/**
 * Created by amarendra on 24/3/17.
 */
public class MongoEnvironmentRepository implements EnvironmentRepository {
    public static final String PROPERTY = "property";
    private static final String LABEL = "label";
    private static final String PROFILE = "profile";
    private static final String DEFAULT = "default";
    private static final String DEFAULT_PROFILE = null;
    private static final String DEFAULT_LABEL = "master";

    private MongoTemplate mongoTemplate;

    public MongoEnvironmentRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Environment findOne(String name, String profile, String label) {
        String[] profilesArr = StringUtils.commaDelimitedListToStringArray(profile);
        List<String> profiles = new ArrayList<String>(Arrays.asList(profilesArr.clone()));
        for (int i = 0; i < profiles.size(); i++) {
            if (DEFAULT.equals(profiles.get(i))) {
                profiles.set(i, DEFAULT_PROFILE);
            }
        }
        profiles.add(DEFAULT_PROFILE); // Default configuration will have 'null' profile
        profiles = sortedUnique(profiles);

        List<String> labels = Arrays.asList(label, DEFAULT_LABEL); // Default configuration will have 'null' label
        labels = sortedUnique(labels);

        Query query = new Query();
        query.addCriteria(Criteria.where(PROFILE).in(profiles.toArray()));
        query.addCriteria(Criteria.where(LABEL).in(labels.toArray()));

        Environment environment;
        try {
            List<MongoPropertySource> sources = mongoTemplate.find(query, MongoPropertySource.class, name);
            sortSourcesByLabel(sources, labels);
            sortSourcesByProfile(sources, profiles);
            environment = new Environment(name, profilesArr);
            for (MongoPropertySource propertySource : sources) {
                String sourceName = generateSourceName(name, propertySource);
                //Map<String, Object> flatSource = mapFlattener.flatten(propertySource.getSource());
                Map<String, Object> source = (LinkedHashMap<String, Object>) propertySource.getSource().get(PROPERTY);
                source = beautifySource(source);
                PropertySource propSource = new PropertySource(sourceName, source);
                if (environment.getPropertySources().size() > 0) {
                    environment.getPropertySources().remove(0);
                }
                environment.add(propSource);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot load environment", e);
        }

        return environment;
    }

    public static Map<String, Object> beautifySource(Map<String, Object> source) {
        Map<String,Object> beautifySource = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, val) -> {
                String value = String.valueOf(val);
                beautifySource.put(key, value.replace(MAP_KEY_DOT_REPLACEMENT,"."));
            });
        }
        return beautifySource;
    }



    private ArrayList<String> sortedUnique(List<String> values) {
        return new ArrayList<String>(new LinkedHashSet<String>(values));
    }

    private void sortSourcesByLabel(List<MongoPropertySource> sources,
                                    final List<String> labels) {
        Collections.sort(sources, new Comparator<MongoPropertySource>() {

            @Override
            public int compare(MongoPropertySource s1, MongoPropertySource s2) {
                int i1 = labels.indexOf(s1.getLabel());
                int i2 = labels.indexOf(s2.getLabel());
                return Integer.compare(i1, i2);
            }

        });
    }

    private void sortSourcesByProfile(List<MongoPropertySource> sources,
                                      final List<String> profiles) {
        Collections.sort(sources, new Comparator<MongoPropertySource>() {

            @Override
            public int compare(MongoPropertySource s1, MongoPropertySource s2) {
                int i1 = profiles.indexOf(s1.getProfile());
                int i2 = profiles.indexOf(s2.getProfile());
                return Integer.compare(i1, i2);
            }

        });
    }

    private String generateSourceName(String environmentName, MongoPropertySource source) {
        String sourceName;
        String profile = source.getProfile() != null ? source.getProfile() : DEFAULT;
        String label = source.getLabel();
        if (label != null) {
            sourceName = String.format("%s-%s-%s", environmentName, profile, label);
        }
        else {
            sourceName = String.format("%s-%s", environmentName, profile);
        }
        return sourceName;
    }

    public static String beautifySource(String key) {
        String newKey = key.replace(MAP_KEY_DOT_REPLACEMENT,".");
        return newKey;
    }

    public static String uglifySource(String key) {
        String newKey = key.replace(".",MAP_KEY_DOT_REPLACEMENT);
        return newKey;
    }

    public static class MongoPropertySource {

        private String profile;
        private String label;
        private LinkedHashMap<String, Object> source = new LinkedHashMap<String, Object>();

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public LinkedHashMap<String, Object> getSource() {
            return source;
        }

        public void setSourceByKey(String key, Object val) {
            source.put(key, val);
        }

        public void setSource(LinkedHashMap<String, Object> source) {
            this.source = source;
        }

    }
}

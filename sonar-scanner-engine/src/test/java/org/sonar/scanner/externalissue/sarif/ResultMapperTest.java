/*
 * SonarQube
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.externalissue.sarif;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.NewExternalIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rules.RuleType;
import org.sonar.core.sarif.Location;
import org.sonar.core.sarif.Result;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(DataProviderRunner.class)
public class ResultMapperTest {

  private static final String RULE_ID = "test_rules_id";
  private static final String DRIVER_NAME = "driverName";

  @Mock
  private LocationMapper locationMapper;

  @Mock
  private SensorContext sensorContext;

  @Mock
  private NewExternalIssue newExternalIssue;

  @Mock
  private NewIssueLocation newExternalIssueLocation;

  @Mock
  private Result result;

  @InjectMocks
  ResultMapper resultMapper;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(result.getRuleId()).thenReturn(RULE_ID);
    when(sensorContext.newExternalIssue()).thenReturn(newExternalIssue);
    when(locationMapper.fillIssueInFileLocation(any(), any(), any())).thenReturn(newExternalIssueLocation);
    when(locationMapper.fillIssueInProjectLocation(any(), any())).thenReturn(newExternalIssueLocation);
    when(newExternalIssue.newLocation()).thenReturn(newExternalIssueLocation);
  }

  @Test
  public void mapResult_mapsSimpleFieldsCorrectly() {
    NewExternalIssue newExternalIssue = resultMapper.mapResult(DRIVER_NAME, result);

    verify(newExternalIssue).type(RuleType.VULNERABILITY);
    verify(newExternalIssue).engineId(DRIVER_NAME);
    verify(newExternalIssue).severity(ResultMapper.DEFAULT_SEVERITY);
    verify(newExternalIssue).ruleId(RULE_ID);
  }

  @Test
  public void mapResult_ifRuleIdMissing_fails() {
    when(result.getRuleId()).thenReturn(null);
    assertThatNullPointerException()
      .isThrownBy(() -> resultMapper.mapResult(DRIVER_NAME, result))
      .withMessage("No ruleId found for issue thrown by driver driverName");
  }

  @Test
  public void mapResult_whenLocationExists_createsFileLocation() {
    Location location = mock(Location.class);
    when(result.getLocations()).thenReturn(Set.of(location));

    NewExternalIssue newExternalIssue = resultMapper.mapResult(DRIVER_NAME, result);

    verify(locationMapper).fillIssueInFileLocation(result, newExternalIssueLocation, location);
    verifyNoMoreInteractions(locationMapper);
    verify(newExternalIssue).at(newExternalIssueLocation);
    verify(newExternalIssue, never()).addLocation(any());
    verify(newExternalIssue, never()).addFlow(any());
  }

  @Test
  public void mapResult_whenLocationExistsButLocationMapperReturnsNull_createsProjectLocation() {
    Location location = mock(Location.class);
    when(result.getLocations()).thenReturn(Set.of(location));
    when(locationMapper.fillIssueInFileLocation(any(), any(), any())).thenReturn(null);

    NewExternalIssue newExternalIssue = resultMapper.mapResult(DRIVER_NAME, result);

    verify(locationMapper).fillIssueInProjectLocation(result, newExternalIssueLocation);
    verify(newExternalIssue).at(newExternalIssueLocation);
    verify(newExternalIssue, never()).addLocation(any());
    verify(newExternalIssue, never()).addFlow(any());
  }

  @Test
  public void mapResult_whenLocationNotFound_createsProjectLocation() {
    NewExternalIssue newExternalIssue = resultMapper.mapResult(DRIVER_NAME, result);

    verify(locationMapper).fillIssueInProjectLocation(result, newExternalIssueLocation);
    verifyNoMoreInteractions(locationMapper);
    verify(newExternalIssue).at(newExternalIssueLocation);
    verify(newExternalIssue, never()).addLocation(any());
    verify(newExternalIssue, never()).addFlow(any());
  }

  @Test
  public void mapResult_mapsErrorLevel_toCriticalSeverity() {
    when(result.getLevel()).thenReturn("error");
    NewExternalIssue newExternalIssue = resultMapper.mapResult(DRIVER_NAME, result);
    verify(newExternalIssue).severity(Severity.CRITICAL);
  }

  @DataProvider
  public static Object[][] level_severity_mapping() {
    return new Object[][] {
      {"error", Severity.CRITICAL},
      {"warning", Severity.MAJOR},
      {"note", Severity.MINOR},
      {"none", Severity.INFO},
      {"anything else", ResultMapper.DEFAULT_SEVERITY},
    };
  }

  @Test
  @UseDataProvider("level_severity_mapping")
  public void mapResult_mapsCorrectlyLevelToSeverity(String level, Severity severity) {
    when(result.getLevel()).thenReturn(level);
    NewExternalIssue newExternalIssue = resultMapper.mapResult(DRIVER_NAME, result);
    verify(newExternalIssue).severity(severity);
  }

}

import React, { useContext, useEffect, useState } from "react";
import {
  Button,
  DataTable,
  Heading,
  Section,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Loading,
  Grid,
  Column,
} from "@carbon/react";
import { useHistory } from "react-router-dom";
import { FormattedMessage, useIntl } from "react-intl";
import { confirmAlert } from "react-confirm-alert";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { NotificationContext } from "../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification.js";
import {
  deleteFromOpenElisServerFullResponse,
  getFromOpenElisServer,
} from "../../utils/Utils.js";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "customRole.page.title",
    link: "/MasterListsPage/roleManagement",
  },
];

const headers = [
  { key: "name", header: "customRole.fields.name" },
  { key: "description", header: "customRole.fields.description" },
];

function RoleManagement() {
  const history = useHistory();
  const intl = useIntl();
  const { addNotification, notificationVisible, setNotificationVisible } =
    useContext(NotificationContext);
  const [loading, setLoading] = useState(true);
  const [roles, setRoles] = useState([]);

  const loadRoles = () => {
    setLoading(true);
    getFromOpenElisServer("/rest/custom-roles/catalog", (response) => {
      setRoles(Array.isArray(response?.customRoles) ? response.customRoles : []);
      setLoading(false);
    });
  };

  useEffect(() => {
    loadRoles();
  }, []);

  const handleDelete = (roleId) => {
    confirmAlert({
      title: intl.formatMessage({
        id: "customRole.delete.confirm.title",
        defaultMessage: "Delete custom role",
      }),
      message: intl.formatMessage({
        id: "customRole.delete.confirm.body",
        defaultMessage:
          "This custom role will no longer be available for users.",
      }),
      buttons: [
        {
          label: intl.formatMessage({
            id: "customRole.delete.confirm.ok",
            defaultMessage: "Delete",
          }),
          onClick: () => {
            deleteFromOpenElisServerFullResponse(
              `/rest/custom-roles/${roleId}`,
              async (response) => {
                if (response.ok) {
                  addNotification({
                    kind: NotificationKinds.success,
                    title: intl.formatMessage({ id: "notification.title" }),
                    message: intl.formatMessage({
                      id: "customRole.delete.success",
                      defaultMessage: "Custom role deleted successfully",
                    }),
                  });
                  setNotificationVisible(true);
                  loadRoles();
                  return;
                }

                let message = intl.formatMessage({ id: "server.error.msg" });
                try {
                  const payload = await response.json();
                  message = payload?.message || message;
                } catch (_error) {
                  // ignore parse failure
                }
                addNotification({
                  kind: NotificationKinds.error,
                  title: intl.formatMessage({ id: "notification.title" }),
                  message,
                });
                setNotificationVisible(true);
              },
            );
          },
        },
        {
          label: intl.formatMessage({
            id: "customRole.delete.confirm.cancel",
            defaultMessage: "Cancel",
          }),
        },
      ],
    });
  };

  if (loading) {
    return <Loading />;
  }

  return (
    <div className="adminPageContent">
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth>
        <Column lg={16} md={8} sm={4}>
          {notificationVisible === true ? <AlertDialog /> : ""}
          <Section>
            <Heading>
              <FormattedMessage
                id="customRole.page.title"
                defaultMessage="Custom Role Management"
              />
            </Heading>
          </Section>
          <br />
          <Button onClick={() => history.push("/MasterListsPage/roleEdit?ID=0")}>
            <FormattedMessage
              id="customRole.actions.add"
              defaultMessage="Create custom role"
            />
          </Button>
          <br />
          <br />
          <DataTable rows={roles} headers={headers}>
            {({ rows, headers, getHeaderProps, getRowProps }) => (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      {headers.map((header) => (
                        <TableHeader
                          key={header.key}
                          {...getHeaderProps({ header })}
                        >
                          <FormattedMessage
                            id={header.header}
                            defaultMessage={header.key}
                          />
                        </TableHeader>
                      ))}
                      <TableHeader>
                        <FormattedMessage
                          id="customRole.actions.title"
                          defaultMessage="Actions"
                        />
                      </TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((row) => (
                      <TableRow key={row.id} {...getRowProps({ row })}>
                        {row.cells.map((cell) => (
                          <TableCell key={cell.id}>{cell.value}</TableCell>
                        ))}
                        <TableCell>
                          <Button
                            kind="ghost"
                            size="sm"
                            onClick={() =>
                              history.push(
                                `/MasterListsPage/roleEdit?ID=${row.id}`,
                              )
                            }
                          >
                            <FormattedMessage
                              id="customRole.actions.edit"
                              defaultMessage="Edit"
                            />
                          </Button>
                          <Button
                            kind="danger--ghost"
                            size="sm"
                            onClick={() => handleDelete(row.id)}
                          >
                            <FormattedMessage
                              id="customRole.actions.delete"
                              defaultMessage="Delete"
                            />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DataTable>
        </Column>
      </Grid>
    </div>
  );
}

export default RoleManagement;

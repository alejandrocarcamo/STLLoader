package acarcamo.loader;

import javafx.geometry.Point3D;
import javafx.scene.shape.Mesh;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashMap;

/**
 * Based on
 * http://www.fabbers.com/tech/STL_Format#Sct_binary
 * https://all3dp.com/what-is-stl-file-format-extension-3d-printing/#pointfour
 * https://stackoverflow.com/questions/38071758/what-is-use-of-getnormals-method-in-trianglemesh-javafx
 * https://docs.oracle.com/javase/8/javafx/api/javafx/scene/shape/TriangleMesh.html
 *
 */
public class ExperimentalStlLoader
{
    private static final Logger logger = LogManager.getLogger(ExperimentalStlLoader.class);

    private HashMap<String, Point3D> puntosUnicos;
    private HashMap<Point3D, Integer> diccionarioPuntos;

    /**
     * Method to load a STL and returns a javafx.scene.shape.Mesh (javafx.scene.shape.TriangleMesh to be specific)
     * It will read the STL file to see if it is an ASCII STL, and based on the result will process it.
     * If the kind of STL is known (binary or ASCII) it will be slightly faster to call the specific method. If it is
     * not known, this is a good approach.
     * For further documentation review the other methods (loadBinaryStl and loadAsciiStl)
     * @param path full path to the file
     * @return javafx.scene.shape.Mesh that can be rendered
     * @throws LoadException if the underlying method raises an error
     */
    public Mesh loadStl (String path) throws LoadException
    {
        try
        {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            // To know if a STL is a binary file or ASCII file
            // read the second line and verify it contains facet (lowercase). Initially I verified for solid name on
            // the first line, but some models ignore the suggestion of binary files not starting with model
            // so for better compatibility, I included this change
            String tmp = reader.readLine();
            tmp = reader.readLine();
            if (null!=tmp && tmp.contains("facet"))
            {
                reader.close();
                return loadAsciiStl(path);
            }
            else
            {
                reader.close();
                return loadBinaryStl(path);
            }
        } catch (IOException e)
        {
            throw new LoadException("Error importing file ["+path+"]", e);
        }

    }

    /**
     * Method to load a STL and returns a javafx.scene.shape.Mesh (javafx.scene.shape.TriangleMesh to be specific)
     *      This method does not validate compliance with the STL standard and assumes the STL is correct. (It fails with
     *      https://www.thingiverse.com/thing:3877695 which uses nan [not a number] in the normals )
     *      The header and additional information for each face is ignored
     *      No texture information is calculated
     *      Beware: when using this with an ASCII STL file it throws out of memory error. In case of doubt, use the
     *      loadStl method.
     * @param path full path to the file
     * @return javafx.scene.shape.Mesh that can be rendered
     * @throws LoadException if there is an IOException reading the file
     */
    public  Mesh loadBinaryStl(String path) throws LoadException
    {
        TriangleMesh result = null;
        try
        {
            FileChannel fileChannel = (FileChannel) Files.newByteChannel(Paths.get(path), EnumSet.of(StandardOpenOption.READ));
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            // read the header and ignore it
            buffer.position(80);
            int triangulos =  buffer.getInt();
            puntosUnicos = new HashMap<>(triangulos*3);
            diccionarioPuntos = new HashMap<>();
            result = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            result.getTexCoords().addAll(0f,0f);
            Point3D p1,p2,p3;
            Point3D normal;
            int sw;

            for(int i=0; i < triangulos; i++)
            {
                //first the facets - normals 3 coordinates * 4 bytes = 12
                // for normals: https://stackoverflow.com/questions/38071758/what-is-use-of-getnormals-method-in-trianglemesh-javafx
                //
                normal = new Point3D( buffer.getFloat(),buffer.getFloat(),buffer.getFloat());
                result.getNormals().addAll((float)normal.getX(),(float)normal.getY(), (float) normal.getZ());
                //Now we read the coordinates of the 3 vertexes, so 3*4=12 * 3 = 36 bytes
                //As the normals occupy the fist 12 bytes, we use that as initial offset
                // I added this scope for clarity, it's functionally useless
                {
                    //Point 1:
                    p1 = new Point3D(buffer.getFloat(),buffer.getFloat(), buffer.getFloat());
                    if (null == puntosUnicos.putIfAbsent(p1.toString(), p1))
                    {
                        diccionarioPuntos.put(p1, puntosUnicos.size()-1);
                        //We add it to the points
                        result.getPoints().addAll((float) p1.getX(),(float) p1.getY(),(float) p1.getZ());
                        if (puntosUnicos.size() != result.getPoints().size()/3) logger.warn(String.format("ExperimentalStlLoader.loadBinaryStl: puntos unicos != getpuntos (%d, %d)", puntosUnicos.size(), result.getPoints().size()));
                    }
                    //Point 2:
                    p2 = new Point3D(buffer.getFloat(),buffer.getFloat(), buffer.getFloat());
                    if (null == puntosUnicos.putIfAbsent(p2.toString(), p2))
                    {
                        diccionarioPuntos.put(p2, puntosUnicos.size()-1);
                        result.getPoints().addAll((float) p2.getX(),(float) p2.getY(),(float) p2.getZ());
                        if (puntosUnicos.size() != result.getPoints().size()/3) logger.warn(String.format("ExperimentalStlLoader.loadBinaryStl: puntos unicos != getpuntos (%d, %d)", puntosUnicos.size(), result.getPoints().size()));
                    }
                    //Point 3:
                    p3 = new Point3D(buffer.getFloat(),buffer.getFloat(), buffer.getFloat());
                    //I add the points to the indexes
                    if (null == puntosUnicos.putIfAbsent(p3.toString(), p3))
                    {
                        diccionarioPuntos.put(p3, puntosUnicos.size()-1);
                        result.getPoints().addAll((float) p3.getX(),(float) p3.getY(),(float) p3.getZ());
                        if (puntosUnicos.size() != result.getPoints().size()/3) logger.warn(String.format("ExperimentalStlLoader.loadBinaryStl: puntos unicos != getpuntos (%d, %d)", puntosUnicos.size(), result.getPoints().size()));
                    }
                    int firstIndex = result.getNormals().size() / 3-1;
                    //So, my face is P1, P2, P3 and normal
                    // p0, n0, t0, p1, n1, t1, p3, n3, t3,
                    // see https://stackoverflow.com/questions/19459012/how-to-create-custom-3d-model-in-javafx-8
                    // see https://stackoverflow.com/questions/49130485/javafx-shape3d-texturing-dont-strectch-the-image
                    // initial test without normal and textures 0
                    // so P1, 0, P2, 0, P3, 0
                    // I need to add the points and the faces to the mesh
                    result.getFaces().addAll(
                            diccionarioPuntos.get(p1),
                            firstIndex,
                            0, //(int) Math.round(p1.getX()/ (maxX-minX)),
                            diccionarioPuntos.get(p2),
                            firstIndex,
                            0, //(int) Math.round(p1.getY()/ (maxY-minY)),
                            diccionarioPuntos.get(p3),
                            firstIndex,
                            0 //(int) Math.round(p1.getZ()/ (maxZ-minZ))
                    );


                }
                //The additional info, 12+36 length 2, are ignored as of now
                //so I read 2
                buffer.position(buffer.position()+2);
            }

            // close the reader
            buffer= null;

        }
        catch (IOException ex)
        {
            throw new LoadException("Error loading binary STL [" + path + "] ", ex);
        }
        return result;
    }

    /**
     * Method to load a STL and returns a javafx.scene.shape.Mesh (javafx.scene.shape.TriangleMesh to be specific)
     *      This method is focused on clarity. As ASCII STL are less efficient and heavier, they are not widely used.
     *      This method does not validate compliance with the STL standard and assumes the STL is correct.
     *      No texture information is calculated
     *      Beware: when using this with an Binary STL file it will return an empty mesh
     * @param path full path to the file
     * @return javafx.scene.shape.Mesh that can be rendered
     * @throws LoadException if there is an IOException reading the file
     */
    public  Mesh loadAsciiStl(String path) throws LoadException
    {
        TriangleMesh result = null;
        try
        {
            // create a reader
            BufferedReader reader = new BufferedReader(new FileReader(path));
            // read the first line
            String tmp = reader.readLine();
            if (!tmp.startsWith("solid")) throw new IOException("Invalid ASCII STL ("+path+")");

            puntosUnicos = new HashMap<>();
            diccionarioPuntos = new HashMap<>();

            result = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            result.getTexCoords().addAll(0f,0f);


            while (null != (tmp = reader.readLine()))
            {
                //We only accept facet
                if (tmp.contains("facet"))
                {
                    String[] split = tmp.replaceFirst("facet normal", "").trim().split(" ");
                    Point3D normal = new Point3D(
                            Float.parseFloat(split[0]),
                            Float.parseFloat(split[1]),
                            Float.parseFloat(split[2]));
                    result.getNormals().addAll((float) normal.getX(), (float) normal.getY(), (float) normal.getZ());

                    reader.readLine(); // we ignore the outerloop

                    float coordinateX, coordinateY, coordinateZ;
                    Point3D p1, p2, p3;

                    //Point 1:
                    tmp = reader.readLine();
                    split = tmp.replaceFirst("vertex", "").trim().split(" ");
                    coordinateX = (Float.parseFloat(split[0]));
                    coordinateY = (Float.parseFloat(split[1]));
                    coordinateZ = (Float.parseFloat(split[2]));
                    p1 = new Point3D(coordinateX, coordinateY, coordinateZ);

                    //Point 2:
                    tmp = reader.readLine();
                    split = tmp.replaceFirst("vertex", "").trim().split(" ");
                    coordinateX = (Float.parseFloat(split[0]));
                    coordinateY = (Float.parseFloat(split[1]));
                    coordinateZ = (Float.parseFloat(split[2]));
                    p2 = new Point3D(coordinateX, coordinateY, coordinateZ);

                    //Point 3:
                    tmp = reader.readLine();
                    split = tmp.replaceFirst("vertex", "").trim().split(" ");
                    coordinateX = (Float.parseFloat(split[0]));
                    coordinateY = (Float.parseFloat(split[1]));
                    coordinateZ = (Float.parseFloat(split[2]));
                    p3 = new Point3D(coordinateX, coordinateY, coordinateZ);

                    if (null == puntosUnicos.putIfAbsent(p1.toString(), p1))
                    {
                        diccionarioPuntos.put(p1, puntosUnicos.size() - 1);
                        //We add it to the points
                        result.getPoints().addAll((float) p1.getX(), (float) p1.getY(), (float) p1.getZ());
                        if (puntosUnicos.size() != result.getPoints().size() / 3)
                            logger.warn(String.format("ExperimentalStlLoader.loadBinaryStl: puntos unicos != getpuntos (%d, %d)", puntosUnicos.size(), result.getPoints().size()));
                    }
                    if (null == puntosUnicos.putIfAbsent(p2.toString(), p2))
                    {
                        diccionarioPuntos.put(p2, puntosUnicos.size() - 1);
                        result.getPoints().addAll((float) p2.getX(), (float) p2.getY(), (float) p2.getZ());
                        if (puntosUnicos.size() != result.getPoints().size() / 3)
                            logger.warn(String.format("ExperimentalStlLoader.loadBinaryStl: puntos unicos != getpuntos (%d, %d)", puntosUnicos.size(), result.getPoints().size()));
                    }
                    if (null == puntosUnicos.putIfAbsent(p3.toString(), p3))
                    {
                        diccionarioPuntos.put(p3, puntosUnicos.size() - 1);
                        result.getPoints().addAll((float) p3.getX(), (float) p3.getY(), (float) p3.getZ());
                        if (puntosUnicos.size() != result.getPoints().size() / 3)
                            logger.warn(String.format("ExperimentalStlLoader.loadBinaryStl: puntos unicos != getpuntos (%d, %d)", puntosUnicos.size(), result.getPoints().size()));
                    }
                    int firstIndex = result.getNormals().size() / 3 - 1;
                    //So, my face is P1, P2, P3 and normal
                    // p0, n0, t0, p1, n1, t1, p3, n3, t3,
                    // see https://stackoverflow.com/questions/19459012/how-to-create-custom-3d-model-in-javafx-8
                    // see https://stackoverflow.com/questions/49130485/javafx-shape3d-texturing-dont-strectch-the-image
                    result.getFaces().addAll(
                            diccionarioPuntos.get(p1),
                            firstIndex,
                            0, //(int) Math.round(p1.getX()/ (maxX-minX)),
                            diccionarioPuntos.get(p2),
                            firstIndex,
                            0, //(int) Math.round(p1.getY()/ (maxY-minY)),
                            diccionarioPuntos.get(p3),
                            firstIndex,
                            0 //(int) Math.round(p1.getZ()/ (maxZ-minZ))
                    );
                    reader.readLine(); //endloop
                    reader.readLine(); //endfacet
                }
            }


        }
         catch (IOException ex) {
             throw new LoadException("Error loading ASCII STL [" + path + "] ", ex);
        }
        return result;
    }

    /**
     * In the case the class is to be reused later, it provides a method to clean the inner maps used to load the files.
     * Keep in mind that 3D models can use a lot of VERTEXES and thus make this really heavy.
     * It clears and set to null both private hasmaps, and optionally invokes the garbage collector
     * @param collect boolean to request garbage collection
     */
    public void clear(Boolean collect)
    {
        puntosUnicos.clear();
        puntosUnicos = null;
        diccionarioPuntos.clear();
        diccionarioPuntos= null;
        if (collect) System.gc();
    }

}
